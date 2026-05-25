package com.ai.service.connector;

import com.ai.datasource.dto.DataSourceMutationResponse;
import com.ai.datasource.dto.DataSourceRequest;
import com.ai.model.DataSourceConfig;
import com.ai.repository.DataSourceConfigRepository;
import com.ai.security.SecurityContextHelper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataSourceServiceTest {

    @Test
    void updateKeepsExistingPasswordWhenPasswordBlank() {
        DataSourceConfigRepository repository = mock(DataSourceConfigRepository.class);
        SecurityContextHelper securityContextHelper = mock(SecurityContextHelper.class);
        DataSourceConfig existing = new DataSourceConfig();
        existing.setDatasourceId("ds-1");
        existing.setTenantId("tenant-1");
        existing.setPassword("secret");

        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-1");
        when(repository.findByDatasourceIdAndTenantId("ds-1", "tenant-1")).thenReturn(Optional.of(existing));

        DataSourceService service = new DataSourceService(repository, securityContextHelper);
        DataSourceRequest request = new DataSourceRequest(
                "orders", "mysql", "localhost", 3306, "app", "root", "", "demo", true);

        DataSourceMutationResponse response = service.update("ds-1", request);

        assertTrue(response.success());
        assertEquals("secret", existing.getPassword());
        verify(repository).save(existing);
    }

    @Test
    void updateReturnsFailureWhenDatasourceDoesNotBelongToTenant() {
        DataSourceConfigRepository repository = mock(DataSourceConfigRepository.class);
        SecurityContextHelper securityContextHelper = mock(SecurityContextHelper.class);
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-2");
        when(repository.findByDatasourceIdAndTenantId("ds-1", "tenant-2")).thenReturn(Optional.empty());

        DataSourceService service = new DataSourceService(repository, securityContextHelper);
        DataSourceRequest request = new DataSourceRequest(
                "orders", "mysql", "localhost", 3306, "app", "root", "secret", "demo", true);

        DataSourceMutationResponse response = service.update("ds-1", request);

        assertFalse(response.success());
        verify(repository, org.mockito.Mockito.never()).save(any());
    }
}
