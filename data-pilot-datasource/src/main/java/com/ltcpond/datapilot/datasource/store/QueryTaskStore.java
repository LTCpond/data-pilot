package com.ltcpond.datapilot.datasource.store;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ltcpond.datapilot.datasource.entity.QueryAttemptEntity;
import com.ltcpond.datapilot.datasource.entity.QueryTaskEntity;
import com.ltcpond.datapilot.datasource.mapper.QueryAttemptMapper;
import com.ltcpond.datapilot.datasource.mapper.QueryTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 封装问数任务和每次生成尝试的持久化。 */
@Repository
@RequiredArgsConstructor
public class QueryTaskStore {

    private final QueryTaskMapper taskMapper;
    private final QueryAttemptMapper attemptMapper;

    public QueryTaskEntity insertTask(QueryTaskEntity task) {
        taskMapper.insert(task);
        return task;
    }

    public void updateTask(QueryTaskEntity task) {
        taskMapper.updateById(task);
    }

    public void deleteTask(long taskId) {
        taskMapper.deleteById(taskId);
    }

    public Optional<QueryTaskEntity> findTask(long taskId) {
        return Optional.ofNullable(taskMapper.selectById(taskId));
    }

    public List<QueryTaskEntity> findTasks(long datasourceId) {
        return taskMapper.selectList(Wrappers.<QueryTaskEntity>lambdaQuery()
                .eq(QueryTaskEntity::getDatasourceId, datasourceId)
                .orderByDesc(QueryTaskEntity::getCreatedAt)
                .last("LIMIT 100"));
    }

    /** 应用启动时只清理异步非终态任务，避免重复调用模型或业务数据库。 */
    public List<QueryTaskEntity> findInterruptedAsyncTasks(Set<String> terminalStatuses) {
        return taskMapper.selectList(Wrappers.<QueryTaskEntity>lambdaQuery()
                .eq(QueryTaskEntity::getExecutionMode, "ASYNC")
                .notIn(QueryTaskEntity::getStatus, terminalStatuses));
    }

    public QueryAttemptEntity insertAttempt(QueryAttemptEntity attempt) {
        attemptMapper.insert(attempt);
        return attempt;
    }

    /** 按调用顺序返回任务的模型尝试，供内部评测汇总使用。 */
    public List<QueryAttemptEntity> findAttempts(long taskId) {
        return attemptMapper.selectList(Wrappers.<QueryAttemptEntity>lambdaQuery()
                .eq(QueryAttemptEntity::getTaskId, taskId)
                .orderByAsc(QueryAttemptEntity::getAttemptNo));
    }
}
