package com.ltcpond.datapilot.datasource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ltcpond.datapilot.datasource.entity.DatasourceEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DatasourceMapper extends BaseMapper<DatasourceEntity> {
}
