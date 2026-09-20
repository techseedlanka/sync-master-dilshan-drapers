package com.dilshandrapers.syncmaster.repository;

import com.dilshandrapers.syncmaster.model.AttendanceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, Integer> {

    @Modifying
    @Transactional
    @Query(value = "INSERT IGNORE INTO attendance_logs (EmpId, AuthDateAndTime, AuthDate, AuthTime, Direction, Device, Serial, Person, Card) " +
                   "VALUES (:#{#log.empId}, :#{#log.authDateAndTime}, :#{#log.authDate}, :#{#log.authTime}, :#{#log.direction}, :#{#log.device}, :#{#log.serial}, :#{#log.person}, :#{#log.card})", nativeQuery = true)
    int insertIgnore(@Param("log") AttendanceLog log);
}
