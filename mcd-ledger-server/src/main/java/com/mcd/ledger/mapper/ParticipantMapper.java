package com.mcd.ledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mcd.ledger.domain.Participant;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ParticipantMapper extends BaseMapper<Participant> {
}
