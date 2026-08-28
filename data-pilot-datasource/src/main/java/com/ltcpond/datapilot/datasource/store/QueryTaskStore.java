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

    /** 新增问数任务，并返回带自增 ID 的实体。 */
    public QueryTaskEntity insertTask(QueryTaskEntity task) {
        taskMapper.insert(task);
        return task;
    }

    /** 按主键更新问数任务的状态、生成信息和执行指标。 */
    public void updateTask(QueryTaskEntity task) {
        taskMapper.updateById(task);
    }

    /** 删除尚未开始执行的任务记录。 */
    public void deleteTask(long taskId) {
        taskMapper.deleteById(taskId);
    }

    /** 按任务 ID 查找问数任务。 */
    public Optional<QueryTaskEntity> findTask(long taskId) {
        return Optional.ofNullable(taskMapper.selectById(taskId));
    }

    /** 返回指定数据源最近 100 条问数任务。 */
    public List<QueryTaskEntity> findTasks(long datasourceId) {
        return taskMapper.selectList(Wrappers.<QueryTaskEntity>lambdaQuery()
                .eq(QueryTaskEntity::getDatasourceId, datasourceId)
                .orderByDesc(QueryTaskEntity::getCreatedAt)
                .last("LIMIT 100"));
    }

    /** 应用启动时清理非终态任务，避免重复调用模型或业务数据库。 */
    public List<QueryTaskEntity> findInterruptedTasks(Set<String> terminalStatuses) {
        return taskMapper.selectList(Wrappers.<QueryTaskEntity>lambdaQuery()
                .notIn(QueryTaskEntity::getStatus, terminalStatuses));
    }

    /** 记录一次模型生成或修复尝试，便于审计和评测。 */
    public QueryAttemptEntity insertAttempt(QueryAttemptEntity attempt) {
        attemptMapper.insert(attempt);
        return attempt;
    }

}
