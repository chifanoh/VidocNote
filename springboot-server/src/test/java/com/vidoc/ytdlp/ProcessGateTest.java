package com.vidoc.ytdlp;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessGateTest {

    @Test
    void runsWithinTheLimitImmediately() {
        assertThat(new ProcessGate(2).call(() -> "ok")).isEqualTo("ok");
    }

    /** 闸门是内存保护不是限流：超出的调用排队等待，既不抛错也不丢任务 */
    @Test
    void aCallBeyondTheLimitWaitsInsteadOfFailing() throws Exception {
        ProcessGate gate = new ProcessGate(1);
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicBoolean secondRan = new AtomicBoolean(false);

        Thread first = new Thread(() -> gate.call(() -> {
            inside.countDown();
            await(releaseFirst);
            return null;
        }));
        first.start();
        assertThat(inside.await(2, TimeUnit.SECONDS)).isTrue();

        Thread second = new Thread(() -> gate.call(() -> {
            secondRan.set(true);
            return null;
        }));
        second.start();
        Thread.sleep(150);
        assertThat(secondRan).as("还在等许可，不该已经跑起来").isFalse();

        releaseFirst.countDown();
        first.join(2000);
        second.join(2000);
        assertThat(secondRan).as("许可释放后应当跑起来").isTrue();
    }

    /** 异常路径必须归还许可，否则闸门会被慢慢漏空 */
    @Test
    void releasesTheSlotEvenWhenTheCallFails() {
        ProcessGate gate = new ProcessGate(1);

        assertThatThrownBy(() -> gate.call(() -> {
            throw new YtDlpException("boom");
        })).isInstanceOf(YtDlpException.class);

        assertThat(gate.call(() -> "still works")).isEqualTo("still works");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
