package com.orabank.smsbanking.mapper;

import com.orabank.smsbanking.dto.request.SmsRequestDto;
import com.orabank.smsbanking.entity.SmsLog;
import com.orabank.smsbanking.entity.enums.SmsDirection;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;

/**
 * Mapper interface for converting between SmsLog entity and DTOs.
 * Uses MapStruct for efficient code generation.
 */
@Mapper(componentModel = "spring")
public interface SmsLogMapper {
    
    SmsLogMapper INSTANCE = Mappers.getMapper(SmsLogMapper.class);
    
    /**
     * Converts a timestamp in milliseconds to LocalDateTime.
     *
     * @param timestampMillis timestamp en millisecondes depuis epoch
     * @return LocalDateTime correspondant
     */
    @Named("toLocalDateTime")
    default LocalDateTime toLocalDateTime(Long timestampMillis) {
        if (timestampMillis == null) {
            return null;
        }
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(timestampMillis), java.time.ZoneId.systemDefault());
    }
    
    /**
     * Converts an SmsRequestDto to an SmsLog entity for incoming SMS.
     *
     * @param dto the SMS request DTO
     * @return the SMS log entity with mapped fields
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "direction", constant = "INCOMING")
    @Mapping(target = "sender", source = "from")
    @Mapping(target = "timestamp", source = "timestamp", qualifiedByName = "toLocalDateTime")
    @Mapping(target = "relatedSmsId", ignore = true)
    @Mapping(target = "errorMessage", ignore = true)
    @Mapping(target = "processedSuccessfully", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    SmsLog toEntity(SmsRequestDto dto);
    
    /**
     * Creates an outgoing SMS log entry.
     *
     * @param toPhone the recipient phone number
     * @param fromPhone the sender phone number
     * @param content the message content
     * @return the SMS log entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "direction", constant = "OUTGOING")
    @Mapping(target = "sender", source = "fromPhone")
    @Mapping(target = "to", source = "toPhone")
    @Mapping(target = "body", source = "content")
    @Mapping(target = "relatedSmsId", ignore = true)
    @Mapping(target = "errorMessage", ignore = true)
    @Mapping(target = "timestamp", ignore = true)
    @Mapping(target = "processedSuccessfully", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    SmsLog createOutgoingLog(String toPhone, String fromPhone, String content);
}
