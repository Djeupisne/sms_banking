package com.orabank.smsbanking.repository;

import com.orabank.smsbanking.entity.SmsLog;
import com.orabank.smsbanking.entity.enums.SmsDirection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SmsLogRepository extends JpaRepository<SmsLog, Long> {
    
    List<SmsLog> findByDirection(SmsDirection direction);
    
    List<SmsLog> findByToOrSender(String to, String sender);
    
    List<SmsLog> findByDirectionAndToOrSender(SmsDirection direction, String to, String sender);
}
