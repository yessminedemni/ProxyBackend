package org.example.yasspfe.reposotories;

import org.example.yasspfe.entities.MySQLProxyConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProxyConfigRepository extends JpaRepository<MySQLProxyConfig, Long> {
    MySQLProxyConfig findFirstByOrderByIdDesc(); // <-- Required
}
