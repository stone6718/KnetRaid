package kr.knetsoft.knetraid.core;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 비동기(DB 등) 작업의 진행 개수를 추적한다.
 * 서버 종료(onDisable) 시 아직 끝나지 않은 작업이 데이터를 마저 저장할 수 있도록
 * 짧은 시간 대기하는 데 사용한다.
 */
public class AsyncTaskTracker {

    private final AtomicInteger pending = new AtomicInteger(0);

    public void begin() {
        pending.incrementAndGet();
    }

    public void end() {
        pending.decrementAndGet();
    }

    /**
     * 진행 중인 작업이 모두 끝날 때까지 최대 timeoutMillis만큼 대기한다.
     *
     * @return 시간 내에 모두 끝났으면 true
     */
    public boolean awaitIdle(long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (pending.get() > 0 && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return pending.get() == 0;
    }
}
