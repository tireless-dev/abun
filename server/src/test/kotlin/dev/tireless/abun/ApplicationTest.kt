package dev.tireless.abun

import dev.tireless.abun.sync.BatchRequest
import dev.tireless.abun.sync.SyncTask
import dev.tireless.abun.sync.SyncTaskEvent
import dev.tireless.abun.sync.TaskEventType
import org.h2.jdbcx.JdbcDataSource
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApplicationTest {
    @Test
    fun `root responds with sync server banner`() = testApplication {
        application { module(testServices()) }

        val response = client.get("/")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("abun sync server", response.bodyAsText())
    }

    @Test
    fun `task sync accepts newer hlc and rejects older hlc`() = testApplication {
        application { module(testServices()) }
        val jsonClient = jsonClient()

        val initialResponse = jsonClient.post("/sync/tasks") {
            header(HttpHeaders.Authorization, "Bearer user-1")
            contentType(ContentType.Application.Json)
            setBody(
                BatchRequest(
                    items = listOf(
                        SyncTask(
                            id = "task-1",
                            title = "Write spec",
                            hlcMap = mapOf("title" to "1715959378000-0001-deviceA"),
                            dirtyFields = listOf("title"),
                        ),
                    ),
                ),
            )
        }
        val initialBody = initialResponse.body<BatchRequest<SyncTask>>()
        assertEquals(listOf("title"), initialBody.items.single().acceptedFields)

        val conflictResponse = jsonClient.post("/sync/tasks") {
            header(HttpHeaders.Authorization, "Bearer user-1")
            contentType(ContentType.Application.Json)
            setBody(
                BatchRequest(
                    items = listOf(
                        SyncTask(
                            id = "task-1",
                            title = "Old edit",
                            hlcMap = mapOf("title" to "1715959378000-0000-deviceB"),
                            dirtyFields = listOf("title"),
                        ),
                    ),
                ),
            )
        }

        val conflictBody = conflictResponse.body<BatchRequest<SyncTask>>()
        val item = conflictBody.items.single()
        assertEquals("Write spec", item.title)
        assertTrue(item.acceptedFields.orEmpty().isEmpty())
        assertEquals(listOf("title"), item.rejectedFields)
    }

    @Test
    fun `task events are append only and journal status stays derivable`() = testApplication {
        application { module(testServices()) }
        val jsonClient = jsonClient()

        jsonClient.post("/sync/tasks") {
            header(HttpHeaders.Authorization, "Bearer user-1")
            contentType(ContentType.Application.Json)
            setBody(
                BatchRequest(
                    items = listOf(
                        SyncTask(
                            id = "task-1",
                            title = "Ledger task",
                            hlcMap = mapOf("title" to "1715959378000-0001-deviceA"),
                            dirtyFields = listOf("title"),
                        ),
                    ),
                ),
            )
        }

        val created = SyncTaskEvent(
            id = "event-1",
            taskId = "task-1",
            journalDate = "2026-05-24",
            eventType = TaskEventType.CREATED,
            eventTime = "2026-05-24T09:00:00Z",
        )
        val completed = SyncTaskEvent(
            id = "event-2",
            taskId = "task-1",
            journalDate = "2026-05-28",
            eventType = TaskEventType.COMPLETED,
            eventTime = "2026-05-28T10:00:00Z",
        )

        val firstPush = jsonClient.post("/sync/task-events") {
            header(HttpHeaders.Authorization, "Bearer user-1")
            contentType(ContentType.Application.Json)
            setBody(BatchRequest(items = listOf(created, completed)))
        }.body<BatchRequest<SyncTaskEvent>>()
        assertTrue(firstPush.items.all { it.accepted == true })

        val duplicatePush = jsonClient.post("/sync/task-events") {
            header(HttpHeaders.Authorization, "Bearer user-1")
            contentType(ContentType.Application.Json)
            setBody(BatchRequest(items = listOf(created)))
        }.body<BatchRequest<SyncTaskEvent>>()
        assertFalse(duplicatePush.items.single().accepted ?: true)

        val status = jsonClient.get("/api/tasks/task-1/status") {
            header(HttpHeaders.Authorization, "Bearer user-1")
        }.body<TaskStatusResponse>()
        assertEquals(dev.tireless.abun.sync.TaskStatus.COMPLETED, status.status)

        val journal = jsonClient.get("/api/journals/2026-05-24") {
            header(HttpHeaders.Authorization, "Bearer user-1")
        }.body<List<JournalEntry>>()
        assertEquals(1, journal.size)
        assertEquals(TaskEventType.CREATED, journal.single().eventType)
    }

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
            )
        }
    }

    private fun testServices(): AppServices {
        val dataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:${System.nanoTime()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
            user = "sa"
            password = ""
        }
        return AppServices.forDataSource(dataSource)
    }
}
