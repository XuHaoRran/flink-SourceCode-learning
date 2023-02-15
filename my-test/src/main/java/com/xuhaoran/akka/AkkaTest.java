package com.xuhaoran.akka;

import akka.actor.AbstractActor;
import akka.actor.Actor;
import akka.actor.ActorPath;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.IndirectActorProducer;
import akka.actor.Props;

import akka.dispatch.OnFailure;
import akka.dispatch.OnSuccess;
import akka.util.Timeout;

import org.apache.flink.runtime.rpc.FencedRpcGateway;
import org.apache.flink.runtime.rpc.MainThreadExecutable;
import org.apache.flink.runtime.rpc.RpcGateway;
import org.apache.flink.runtime.rpc.RpcServer;
import org.apache.flink.runtime.rpc.StartStoppable;

import scala.Function1;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * <p>当使用tell方式时，表示仅仅使用异步方式给某个Actor发送消
 * 息，无须等待Actor的响应结果，并且也不会阻塞后续代码的运行
 * 其中，第一个参数为消息，它可以是任何可序列化的数据或对
 * 象 ， 第 二 个 参 数 表 示 发 送 者 ， 一 般 是 另 外 一 个 Actor 的 引 用 ，
 * ActorRef.noSender （ ） 表 示 无 发 送 者 （ 实 际 上 是 一 个 叫 作
 * deadLetters的Actor）。
 * <p>当需要从Actor获取响应结果时，可使用ask方法，ask方法会将返
 * 回结果包装在scala.concurrent.Future中，然后通过异步回调获取返
 * 回结果
 *
 */
public class AkkaTest {
    public static void main(String[] args) {
        // 使用默认配置
        ActorSystem system = ActorSystem.create("sys");

        // 获取本地Actor的ActorRef
        ActorSelection as = system.actorSelection("/path/to/actor");
        Timeout timeout = new Timeout(Duration.create(2, "second"));
        Future<ActorRef> fu = as.resolveOne(timeout);
        fu.onSuccess(new OnSuccess<ActorRef>() {
            @Override
            public void onSuccess(ActorRef actor) throws Throwable, Throwable {
                System.out.println("actor:" + actor);
                actor.tell("hello actor", ActorRef.noSender());
            }
        }, system.dispatcher());
        fu.onFailure(new OnFailure() {
            @Override
            public void onFailure(Throwable failure) throws Throwable, Throwable {
                System.out.println("failure"+ failure);
            }
        }, system.dispatcher());

//        // 构建Actor，获取该Actor的引用，即ActorRef
//        ActorRef helloActor = system.actorOf(Props.create(AkkaTest.class), "helloActor");

        // 给helloActor发送消息
//        helloActor.tell("hello helloActor", ActorRef.noSender());

        system.terminate();


    }


}
