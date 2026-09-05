package com.orabank.smsbanking.mapper;

import com.orabank.smsbanking.dto.request.SmsRequestDto;
import com.orabank.smsbanking.entity.SmsLog;
import com.orabank.smsbanking.entity.enums.SmsDirection;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-09-05T22:30:57+0000",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17.0.20 (Debian)"
)
@Component
public class SmsLogMapperImpl implements SmsLogMapper {

    @Override
    public SmsLog toEntity(SmsRequestDto dto) {
        if ( dto == null ) {
            return null;
        }

        SmsLog.SmsLogBuilder smsLog = SmsLog.builder();

        smsLog.sender( dto.getFrom() );
        smsLog.timestamp( toLocalDateTime( dto.getTimestamp() ) );
        smsLog.to( dto.getTo() );
        smsLog.body( dto.getBody() );

        smsLog.direction( SmsDirection.INCOMING );

        return smsLog.build();
    }

    @Override
    public SmsLog createOutgoingLog(String toPhone, String fromPhone, String content) {
        if ( toPhone == null && fromPhone == null && content == null ) {
            return null;
        }

        SmsLog.SmsLogBuilder smsLog = SmsLog.builder();

        smsLog.to( toPhone );
        smsLog.sender( fromPhone );
        smsLog.body( content );
        smsLog.direction( SmsDirection.OUTGOING );

        return smsLog.build();
    }
}
