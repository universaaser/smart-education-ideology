package com.smartedu.service;

import com.smartedu.entity.KeywordTask;
import com.smartedu.entity.KeywordTaskItem;
import com.smartedu.entity.Resource;
import com.smartedu.mapper.KeywordTaskItemMapper;
import com.smartedu.mapper.KeywordTaskMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class KeywordTaskServiceTest {

    @Test
    void shouldReuseExistingResourceWhenAcceptingDuplicateSourceUrl() {
        KeywordTask task = new KeywordTask();
        task.setId(11L);
        task.setCreatorId(5L);

        KeywordTaskItem item = new KeywordTaskItem();
        item.setId(21L);
        item.setTaskId(11L);
        item.setTitle("Smart Sensor Case");
        item.setSourceUrl("https://example.com/sensor");
        item.setStatus("PENDING");

        StubResourceService resourceService = new StubResourceService();
        Resource existing = new Resource();
        existing.setId(31L);
        existing.setTitle("Existing Smart Sensor Case");
        resourceService.existing = existing;

        KeywordTaskItem[] updatedItem = new KeywordTaskItem[1];
        KeywordTaskMapper keywordTaskMapper = mapperProxy(KeywordTaskMapper.class, task, null);
        KeywordTaskItemMapper keywordTaskItemMapper = mapperProxy(KeywordTaskItemMapper.class, item, updatedItem);
        KeywordTaskService service = new KeywordTaskService(keywordTaskMapper, keywordTaskItemMapper, resourceService, null);

        Resource accepted = service.acceptItem(11L, 21L);

        assertEquals(31L, accepted.getId());
        assertEquals("ACCEPTED", item.getStatus());
        assertEquals(31L, item.getResourceId());
        assertEquals(item, updatedItem[0]);
        assertFalse(resourceService.createCalled);
    }

    @SuppressWarnings("unchecked")
    private static <T> T mapperProxy(Class<T> mapperType, Object selectByIdResult, KeywordTaskItem[] updatedItem) {
        return (T) Proxy.newProxyInstance(
                mapperType.getClassLoader(),
                new Class<?>[]{mapperType},
                (proxy, method, args) -> {
                    if ("selectById".equals(method.getName())) {
                        return selectByIdResult;
                    }
                    if ("updateById".equals(method.getName())) {
                        if (updatedItem != null) {
                            updatedItem[0] = (KeywordTaskItem) args[0];
                        }
                        return 1;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static class StubResourceService extends ResourceService {
        private Resource existing;
        private boolean createCalled;

        StubResourceService() {
            super(null, null);
        }

        @Override
        public Resource getBySourceUrl(String sourceUrl) {
            return existing;
        }

        @Override
        public Resource createResource(Resource resource) {
            createCalled = true;
            return resource;
        }
    }
}
