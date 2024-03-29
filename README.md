# 论坛







[项目介绍](#项目介绍) | [技术架构](#技术架构) | [开发环境](#开发环境) | [项目总结](#项目总结) | [效果展示](#效果展示) | [参考资料](#参考资料)





## 项目介绍

一个仿牛客网实现的讨论社区，不仅实现了基本的注册，登录，发帖，评论，点赞，回复功能，同时使用前缀树实现敏感词过滤，使用wkhtmltopdf生成长图和pdf，实现了网站UV和DAU统计，并将用户头像等信息存于七牛云服务器。    

## 技术架构

- Spring Boot
- Spring 、Spring MVC、MyBatis
- Redis、Kafka、Elasticsearch
- Spring Security、Spring Actuator

## 开发环境

- 构建工具：Apache Maven
- 集成开发工具：IDEA
- 数据库：MySQL、Redis
- 应用服务器：Apache Tomcat
- 版本控制工具：Git

## 项目总结



- 使用Spring Security 做权限控制，替代拦截器的拦截控制，并使用自己的认证方案替代Security 认证流程，使权限认证和控制更加方便灵活。
- 使用Redis的set实现点赞，zset实现关注，并使用Redis存储登录ticket和验证码，解决分布式session问题。
- 使用Redis高级数据类型HyperLogLog统计UV(Unique Visitor),使用Bitmap统计DAU(Daily Active User)。
- 使用Kafka处理发送评论、点赞和关注等系统通知，并使用事件进行封装，构建了强大的异步消息系统。
- 使用Elasticsearch做全局搜索，并通过事件封装，增加关键词高亮显示等功能。
- 对热帖排行模块，使用分布式缓存Redis和本地缓存Caffeine作为多级缓存，避免了缓存雪崩，将QPS提升了20倍(10-200)，大大提升了网站访问速度。并使用Quartz定时更新热帖排行。
- 使用 `Jasypt` 实现配置文件敏感信息加密。


## 效果展示
![image](https://user-images.githubusercontent.com/94364292/186612037-e6e6a315-14e3-4b23-82e5-02afc1b14410.png)
![image](https://user-images.githubusercontent.com/94364292/186612298-e0bec4b2-c06d-4a4f-ada8-ff5788a8630a.png)
![image](https://user-images.githubusercontent.com/94364292/186612389-f04577cf-9b19-40f0-9837-c0266ce5c2b0.png)
![image](https://user-images.githubusercontent.com/94364292/186612451-89aafa26-5c35-41ee-b542-8531e84a1676.png)
![image](https://user-images.githubusercontent.com/94364292/186612718-fc242bd8-f1f5-4af8-b86b-8696a40da70a.png)
![image](https://user-images.githubusercontent.com/94364292/186612781-6ddf4208-27a4-4a6e-b83f-84bfef031a1b.png)
![image](https://user-images.githubusercontent.com/94364292/186612879-f770fa05-b0ec-4a58-b5c7-9a9816876cae.png)
![image](https://user-images.githubusercontent.com/94364292/186612941-a923f80d-c4f6-4393-a05a-5c1dcf92b3c5.png)
![image](https://user-images.githubusercontent.com/94364292/186613022-8f37e98e-9044-432f-90b9-e48104fa4005.png)
![image](https://user-images.githubusercontent.com/94364292/186613071-7483d704-a725-478d-9394-68a16e0c9f55.png)
![image](https://user-images.githubusercontent.com/94364292/186613181-c3e6c63e-79d8-4c58-935d-378920065022.png)


