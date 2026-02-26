package ru.protonmod.next.data.repository

import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import retrofit2.Response
import ru.protonmod.next.data.network.*

class VpnRepositoryTest {

    @Mock
    private lateinit var vpnApi: ProtonVpnApi

    private lateinit var repository: VpnRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = VpnRepository(vpnApi)
    }

    @Test
    fun `getServers maps logical loads correctly`() = runTest {
        // Arrange
        val logicalId = "logical_1"
        val physicalId = "physical_1"
        val loadValue = 75

        val logicalServersResponse = LogicalServersResponse(
            code = 1000,
            logicalServers = listOf(
                LogicalServer(
                    id = logicalId,
                    name = "Test Server",
                    tier = 0,
                    features = 0,
                    entryCountry = "US",
                    exitCountry = "US",
                    city = "New York",
                    servers = listOf(
                        PhysicalServer(id = physicalId, domain = "test.protonvpn.com", status = 1)
                    )
                )
            )
        )

        val loadsJson = """
            {
                "LogicalServers": [
                    {
                        "ID": "$logicalId",
                        "Load": $loadValue,
                        "Status": 1
                    }
                ]
            }
        """.trimIndent()

        whenever(vpnApi.getLogicalServers("Bearer token", "session")).thenReturn(logicalServersResponse)
        whenever(vpnApi.getLoads("Bearer token", "session", 0)).thenReturn(
            Response.success(loadsJson.toResponseBody())
        )

        // Act
        val result = repository.getServers("token", "session", 0)

        // Assert
        val servers = result.getOrNull()!!
        assertEquals(1, servers.size)
        assertEquals(loadValue, servers[0].averageLoad)
        assertEquals(loadValue, servers[0].servers[0].load)
    }
}
