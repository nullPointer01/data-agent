package com.ai.agent.tool;

import com.ai.service.connector.DataConnectorService;
import com.ai.service.connector.DataSourceService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentDataSourceToolServiceTest {

    @Test
    void listDataSourcesDelegatesToDataSourceService() {
        DataSourceService dataSourceService = mock(DataSourceService.class);
        when(dataSourceService.listEnabledForAgent()).thenReturn("- orders [mysql]");
        AgentDataSourceToolService service =
                new AgentDataSourceToolService(mock(DataConnectorService.class), dataSourceService);

        String result = service.listDataSources();

        assertEquals("- orders [mysql]", result);
    }

    @Test
    void executeSqlUsesAgentRowLimit() {
        DataConnectorService connectorService = mock(DataConnectorService.class);
        when(connectorService.executeReadOnlySql("orders", "select * from t", 200)).thenReturn("rows");
        AgentDataSourceToolService service = new AgentDataSourceToolService(connectorService,
                mock(DataSourceService.class));

        String result = service.executeSql("orders", "select * from t");

        assertEquals("rows", result);
        verify(connectorService).executeReadOnlySql("orders", "select * from t", 200);
    }

    @Test
    void previewDataSourceUsesAgentPreviewLimit() {
        DataConnectorService connectorService = mock(DataConnectorService.class);
        when(connectorService.preview("orders", 50)).thenReturn("preview");
        AgentDataSourceToolService service = new AgentDataSourceToolService(connectorService,
                mock(DataSourceService.class));

        String result = service.previewDataSource("orders");

        assertEquals("preview", result);
        verify(connectorService).preview("orders", 50);
    }
}
