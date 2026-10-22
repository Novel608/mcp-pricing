package com.mcd.ledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mcd.ledger.domain.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
