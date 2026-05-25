package system.assessment.defense;

import com.alicp.jetcache.anno.config.EnableMethodCache;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan(basePackages = "system.assessment.defense.infrastructure.repository.dao.mapper")
@EnableRetry
@EnableScheduling
@EnableAsync
@EnableMethodCache(basePackages = "system.assessment.defense.application.manage")
public class DefenseAssessmentSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(DefenseAssessmentSystemApplication.class, args);
    }

}
