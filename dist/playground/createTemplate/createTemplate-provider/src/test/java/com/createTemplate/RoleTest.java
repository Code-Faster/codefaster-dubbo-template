package com.createTemplate;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.createTemplate.api.base.dubbo.service.RoleService;
import com.createTemplate.provider.Application;

/**
 * 单元测试
 *
 * @author libiqi
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)

public class RoleTest {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    RoleService roleService;

    @Test
    public void getMenu() {
        List<Map> menu = roleService.getMenu(1L);
        logger.error("\r\ntest1------" + menu.toString());
    }

}
