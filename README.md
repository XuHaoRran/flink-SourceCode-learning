# Flink 学习

---

<p>flink源码中文注释学习版，目前最新更新为<strong>flink-1.16</strong>。
<p>持续学习~持续更新中！
<p>立志将这个项目做大做全，希望能帮助到各位！

# 构建源码

---

**所需环境：**
* Unix-like environment (we use Linux, Mac OS X, Cygwin, WSL)
* Git
* Maven (we recommend version 3.2.5 and require at least 3.1.1)
* Java 8 or 11 (Java 9 or 10 may work)

**构建源码：**

`
git clone https://github.com/apache/flink.git
`
<p> 在maven的settings.xml添加以下代码，用于添加阿里云镜像：

```
<mirror>
  <id>alimaven</id>
  <mirrorOf>central</mirrorOf>
  <name>aliyun maven</name>
  <url>https://maven.aliyun.com/repository/central</url>
</mirror>
```
<p>进入flink目录

`mvn clean package -Dmaven.test.skip=true`

<p>等待10分钟构建完成后，进入以下文件运行测试是否构建成功：

`flink-examples/flink-examples-streaming/src/main/java/org/apache/flink/streaming/examples/wordcount/WordCount.java`

# 实例入口代码库

---

可以进入实例代码断点逐步学习：
* 官方实例：`flink-examples/flink-examples-streaming/src/main/java/org/apache/flink/streaming/examples`
* 学习实例：`my-test/src/main/java/com/xuhaoran`

# 参考资源

---

*  https://github.com/apache/flink
*  尚硅谷
* 《Flink核心技术 源码剖析与特性开发》黄伟哲
* 《Flink内核原理与实现》冯飞


