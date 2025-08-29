package com.projectboard.payment;

import com.projectboard.boardproject.config.SecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication(exclude = {
        SecurityAutoConfiguration.class,              // 웹 시큐리티 자동구성 비활성화
        ManagementWebSecurityAutoConfiguration.class  // 액추에이터 시큐리티 자동구성 비활성화
})
// TODO(결제 API 초안 완성 후): payment 프로필용 보안 정책으로 전환하고 exclude 제거
// 참고: 현재는 payment 관련 모든 경로 모두 인증 없이 열려 있음 (개발용)
public class PaymentSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentSystemApplication.class, args);
    }

}
