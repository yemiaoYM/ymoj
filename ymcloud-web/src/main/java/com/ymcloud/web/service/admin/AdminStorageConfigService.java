package com.ymcloud.web.service.admin;

import com.ymcloud.pojo.dto.admin.StorageConfigDTO;
import com.ymcloud.pojo.vo.common.StorageConfigVO;

import java.util.List;

public interface AdminStorageConfigService {

    List<StorageConfigVO> list();

    void create(StorageConfigDTO dto);

    void update(String key, StorageConfigDTO dto);

    void delete(String key);

    void test(String key);

    void activate(String key);
}
