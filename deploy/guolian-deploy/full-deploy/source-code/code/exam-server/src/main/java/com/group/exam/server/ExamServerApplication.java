package com.group.exam.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.group.exam")
@MapperScan({
        "com.group.exam.system.mapper",
        "com.group.exam.learn.mapper",
        "com.group.exam.question.mapper",
        "com.group.exam.exam.mapper",
        "com.group.exam.stat.mapper",
        "com.group.exam.practice.mapper"
})
public class ExamServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExamServerApplication.class, args);
    }
}
