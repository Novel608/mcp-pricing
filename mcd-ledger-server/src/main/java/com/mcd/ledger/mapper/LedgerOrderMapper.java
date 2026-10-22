package com.mcd.ledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mcd.ledger.domain.LedgerOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LedgerOrderMapper extends BaseMapper<LedgerOrder> {
}
