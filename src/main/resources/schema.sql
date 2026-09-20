CREATE TABLE IF NOT EXISTS `attendance_logs` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `EmpId` varchar(100) DEFAULT NULL,
  `AuthDateAndTime` timestamp NULL DEFAULT NULL,
  `AuthDate` date DEFAULT NULL,
  `AuthTime` time DEFAULT NULL,
  `Direction` varchar(100) DEFAULT NULL,
  `Device` varchar(500) DEFAULT NULL,
  `Serial` varchar(500) DEFAULT NULL,
  `Person` varchar(500) DEFAULT NULL,
  `Card` varchar(100) DEFAULT NULL,
  `synctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `unique_punch` (`EmpId`, `AuthDateAndTime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
