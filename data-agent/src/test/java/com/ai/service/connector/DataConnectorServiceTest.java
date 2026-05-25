package com.ai.service.connector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class DataConnectorServiceTest {

    @Test
    void rejectsMutatingSqlBeforeDatasourceLookup() {
        DataConnectorLookupService lookupService = mock(DataConnectorLookupService.class);
        DataConnectorService service = new DataConnectorService(
                lookupService,
                new DataConnectorSqlPolicy(),
                mock(DataConnectorJdbcService.class),
                mock(DataConnectorHttpService.class));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.executeReadOnlySql("demo", "delete from orders", 10));
        assertTrue(error.getMessage().contains("安全限制"));
        verifyNoInteractions(lookupService);
    }
}
