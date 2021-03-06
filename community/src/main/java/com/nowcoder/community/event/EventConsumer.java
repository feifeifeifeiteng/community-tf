package com.nowcoder.community.event;

import com.alibaba.fastjson.JSONObject;
import com.nowcoder.community.entity.DiscussPost;
import com.nowcoder.community.entity.Event;
import com.nowcoder.community.entity.Message;
import com.nowcoder.community.service.DiscussPostService;
import com.nowcoder.community.service.ElasticSearchService;
import com.nowcoder.community.service.MessageService;
import com.nowcoder.community.util.CommunityConstant;
import com.nowcoder.community.util.CommunityUtil;
import com.qiniu.common.QiniuException;
import com.qiniu.common.Zone;
import com.qiniu.http.Response;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import com.qiniu.util.StringMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

@Component
@Slf4j
public class EventConsumer implements CommunityConstant {
    @Autowired
    private MessageService messageService;

    @Autowired
    private DiscussPostService discussPostService;

    @Autowired
    private ElasticSearchService elasticSearchService;

    @Value("${wk.image.command}")
    private String wkImageCommand;

    @Value("${wk.image.storage}")
    private String wkImageStorage;

    @Value("${qiniu.key.access}")
    private String accessKey;

    @Value("${qiniu.key.secret}")
    private String secretKey;

    @Value("${qiniu.bucket.name}")
    private String bucketName;

    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;


    @KafkaListener(topics = {TOPIC_COMMENT, TOPIC_LIKE, TOPIC_FOLLOW})
    public void handleMessage(ConsumerRecord record) {
        Event event = getEventFromRecord(record);
        if (event == null) {
            return;
        }
        // ??????????????????
        Message message = new Message()
                .setFromId(SYSTEM_USER_ID)
                .setToId(event.getEntityUserId())
                .setConversationId(event.getTopic())
                .setStatus(0)
                .setCreateTime(new Date());
        Map<String, Object> map = new HashMap<>();
        map.put("userId", event.getUserId());
        map.put("entityType", event.getEntityType());
        map.put("entityId", event.getEntityId());

        if (!event.getData().isEmpty()) {
            for (Map.Entry<String, Object> entry : event.getData().entrySet()) {
                map.put(entry.getKey(), entry.getValue());
            }
        }

        message.setContent(JSONObject.toJSONString(map));
        messageService.addMessage(message);
    }

    @KafkaListener(topics = {TOPIC_PUBLISH})
    public void handlePublishMessage(ConsumerRecord record) {
        Event event = getEventFromRecord(record);
        if (event == null) {
            return;
        }
        DiscussPost post = discussPostService.findDiscussPostById(event.getEntityId());
        elasticSearchService.saveDiscussPost(post);
    }

    @KafkaListener(topics = {TOPIC_DELETE})
    public void handleDeleteMessage(ConsumerRecord record) {
        Event event = getEventFromRecord(record);
        if (event == null) {
            return;
        }
        elasticSearchService.deleteDiscussPost(event.getEntityId());
    }

    @KafkaListener(topics = {TOPIC_SHARE})
    public void handleShareMessage(ConsumerRecord record) {
        Event event = getEventFromRecord(record);
        if (event == null) {
            return;
        }
        String url = (String) event.getData().get("url");
        String filename = (String) event.getData().get("filename");
        String suffix = (String) event.getData().get("suffix");
        String cmd = wkImageCommand + " --quality 75 " + url + " " + wkImageStorage + "/" + filename + suffix;
        try {
            Runtime.getRuntime().exec(cmd);
            log.info("??????????????????: " + cmd);
        } catch (IOException exception) {
            log.error("??????????????????: " + exception.getMessage());
        }
        // ????????????????????????????????????????????????????????????????????????
        UploadTask uploadTask = new UploadTask(filename, suffix);
        Future future = taskScheduler.scheduleAtFixedRate(uploadTask, 500);
        uploadTask.setFuture(future);
    }

    class UploadTask implements Runnable {
        private final String filename;
        private final String suffix;
        private Future future;
        // ????????????
        private final long startTime;
        // ????????????
        private int uploadTimes;

        public UploadTask(String filename, String suffix) {
            this.filename = filename;
            this.suffix = suffix;
            this.startTime = System.currentTimeMillis();
            this.uploadTimes = 0;
        }

        public void setFuture(Future future) {
            this.future = future;
        }

        @Override
        public void run() {
            // ??????????????????
            if (System.currentTimeMillis() - startTime > 30000) {
                log.error("??????????????????30??????????????????: " + filename);
                future.cancel(true);
                return;
            }
            // ????????????
            if (uploadTimes > 3) {
                log.error("?????????????????????????????????: " + filename);
                future.cancel(true);
                return;
            }
            String path = wkImageStorage + "/" + filename + suffix;
            File file = new File(path);
            if (file.exists()) {
                log.info(String.format("?????????%d?????????[%s]", ++uploadTimes, filename));
                // ??????????????????
                StringMap policy = new StringMap();
                policy.put("returnBody", CommunityUtil.getJSONString(0));
                // ??????????????????
                Auth auth = Auth.create(accessKey, secretKey);
                String uploadToken = auth.uploadToken(bucketName, filename, 3600, policy);
                // ??????
                UploadManager manager = new UploadManager(new Configuration(Region.huabei()));
                try {
                    Response response = manager.put(path, filename, uploadToken, null, "image/" + suffix, false);
                    JSONObject jsonObject = JSONObject.parseObject(response.bodyString());
                    if (jsonObject == null || jsonObject.get("code") == null || !jsonObject.get("code").toString().equals("0")) {
                        log.info(String.format("???%d???????????????[%s]", uploadTimes, filename));
                    } else {
                        log.info(String.format("???%d???????????????[%s]", uploadTimes, filename));
                        future.cancel(true);
                    }
                } catch (QiniuException e) {
                    log.info(String.format("???%d???????????????[%s]", uploadTimes, filename));
                }
            } else {
                log.info("??????????????????[" + filename + "]");
            }
        }
    }

    private Event getEventFromRecord(ConsumerRecord record) {
        if (record == null || record.value() == null) {
            log.error("?????????????????????");
            return null;
        }
        Event event = JSONObject.parseObject(record.value().toString(), Event.class);
        if (event == null) {
            log.error("?????????????????????");
            return null;
        }
        return event;
    }
}
