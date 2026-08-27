package com.ltcpond.datapilot.api.async;

import com.ltcpond.datapilot.core.query.QueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 启动时终止无法安全重放的遗留异步任务。 */
@Component
@RequiredArgsConstructor
public class AsyncQueryRecovery implements ApplicationRunner {

    private final QueryService queryService;

    /** 应用启动后把重启前遗留的非终态任务统一标记失败。 */
    @Override
    public void run(ApplicationArguments args) {
        queryService.failInterruptedTasks();
    }
}
