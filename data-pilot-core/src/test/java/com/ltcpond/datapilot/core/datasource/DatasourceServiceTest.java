package com.ltcpond.datapilot.core.datasource;

import com.ltcpond.datapilot.common.api.ResponseCode;
import com.ltcpond.datapilot.common.exception.AppException;
import com.ltcpond.datapilot.datasource.connection.ConnectionTestResult;
import com.ltcpond.datapilot.datasource.connection.MysqlConnectionTester;
import com.ltcpond.datapilot.datasource.crypto.CredentialCipher;
import com.ltcpond.datapilot.datasource.entity.DatasourceEntity;
import com.ltcpond.datapilot.datasource.metadata.MysqlMetadataReader;
import com.ltcpond.datapilot.datasource.store.DatasourceStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatasourceServiceTest {

    private DatasourceStore store;
    private MysqlConnectionTester connectionTester;
    private MysqlMetadataReader metadataReader;
    private CredentialCipher credentialCipher;
    private DatasourceService service;

    @BeforeEach
    void setUp() {
        store = mock(DatasourceStore.class);
        connectionTester = mock(MysqlConnectionTester.class);
        metadataReader = mock(MysqlMetadataReader.class);
        credentialCipher = mock(CredentialCipher.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<com.ltcpond.datapilot.core.rag.SchemaIndexService> indexProvider =
                mock(ObjectProvider.class);
        service = new DatasourceService(
                store, connectionTester, metadataReader, credentialCipher, indexProvider);
    }

    @Test
    void shouldNotSaveWhenConnectionTestFails() {
        when(store.findByName("demo")).thenReturn(Optional.empty());
        when(connectionTester.test(any())).thenThrow(
                new AppException(ResponseCode.EXTERNAL_DATASOURCE_OPERATION_FAILED));

        assertThatThrownBy(() -> service.create(command()))
                .isInstanceOfSatisfying(AppException.class, exception -> {
                    assertThat(exception.getResponseCode()).isEqualTo(ResponseCode.DATASOURCE_UNREACHABLE);
                    assertThat(exception).hasMessage("数据源无法连接");
                });
        verify(store, never()).insert(any());
        verify(credentialCipher, never()).encrypt(any());
    }

    @Test
    void shouldEncryptPasswordBeforeSaving() {
        when(store.findByName("demo")).thenReturn(Optional.empty());
        when(connectionTester.test(any())).thenReturn(new ConnectionTestResult("MySQL", "8.0"));
        when(credentialCipher.encrypt("test-secret")).thenReturn("v1:ciphertext");
        when(store.insert(any())).thenAnswer(invocation -> {
            DatasourceEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return entity;
        });

        DatasourceView view = service.create(command());

        assertThat(view.id()).isEqualTo(1L);
        verify(credentialCipher).encrypt("test-secret");
        verify(store).insert(org.mockito.ArgumentMatchers.argThat(entity ->
                "v1:ciphertext".equals(entity.getEncryptedPassword())));
    }

    @Test
    void shouldRejectUnsupportedJdbcUrlAsBadRequest() {
        when(connectionTester.test(any())).thenThrow(new IllegalArgumentException("unsupported"));

        assertThatThrownBy(() -> service.testConnection(new ConnectionTestCommand(
                "jdbc:postgresql://localhost/demo", "reader", "secret")))
                .isInstanceOfSatisfying(AppException.class, exception -> {
                    assertThat(exception.getResponseCode()).isEqualTo(
                            ResponseCode.INVALID_DATASOURCE_CONFIGURATION);
                    assertThat(exception).hasMessage("数据源配置无效");
                });
    }

    @Test
    void shouldKeepStoredMetadataWhenRemoteReadFails() {
        DatasourceEntity entity = new DatasourceEntity();
        entity.setId(9L);
        entity.setJdbcUrl("jdbc:mysql://localhost/demo");
        entity.setUsername("reader");
        entity.setEncryptedPassword("v1:ciphertext");
        when(store.findById(9L)).thenReturn(Optional.of(entity));
        when(credentialCipher.decrypt("v1:ciphertext")).thenReturn("test-secret");
        when(metadataReader.read(any())).thenThrow(
                new AppException(ResponseCode.EXTERNAL_DATASOURCE_OPERATION_FAILED));

        assertThatThrownBy(() -> service.synchronize(9L))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getResponseCode()).isEqualTo(ResponseCode.DATASOURCE_UNREACHABLE));
        verify(store, never()).synchronize(eq(9L), any());
        verify(store).markError(9L);
    }

    private DatasourceCommand command() {
        return new DatasourceCommand(
                "demo", "demo database", "jdbc:mysql://localhost/demo", "reader", "test-secret");
    }
}
