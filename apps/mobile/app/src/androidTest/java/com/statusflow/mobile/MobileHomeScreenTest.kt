package com.statusflow.mobile

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.statusflow.mobile.data.MobileOrderComment
import com.statusflow.mobile.data.MobileOrderDetail
import com.statusflow.mobile.data.MobileOrderHistoryEvent
import com.statusflow.mobile.data.MobileOrderSummary
import com.statusflow.mobile.data.MobileSessionSummary
import com.statusflow.mobile.data.MobileUserSummary
import com.statusflow.mobile.ui.theme.Amber300
import com.statusflow.mobile.ui.theme.StatusFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MobileHomeScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun queueControlsRenderCurrentFilterModel() {
        var selectedStatus: String? = null
        var createTapped = false

        composeRule.setContent {
            StatusFlowTheme {
                Column {
                    StatusCounterCarousel(
                        totalOrders = 10,
                        statusCounts = mapOf("new" to 2, "in_review" to 3),
                        selectedStatus = selectedStatus,
                        onSelectStatus = { selectedStatus = it }
                    )
                    QueueToolbar(
                        searchQuery = "",
                        onSearchQueryChange = {},
                        onCreate = { createTapped = true }
                    )
                }
            }
        }

        composeRule.onNodeWithText("Total").assertIsDisplayed()
        composeRule.onNodeWithTag(MobileUiTags.statusChip("new")).performClick()
        composeRule.onNodeWithTag(MobileUiTags.SEARCH_INPUT).assertIsDisplayed()
        composeRule.onNodeWithText("Create").performClick()

        assertEquals("new", selectedStatus)
        assertEquals(true, createTapped)
    }

    @Test
    fun createScreenSubmitsTrimmedOrderData() {
        var submittedTitle = ""
        var submittedDescription = ""

        composeRule.setContent {
            StatusFlowTheme {
                var title by remember { mutableStateOf("") }
                var description by remember { mutableStateOf("") }
                CreateOrderScreen(
                    title = title,
                    description = description,
                    customerName = "Alex Morgan",
                    isSubmitting = false,
                    isEnabled = true,
                    onTitleChange = { title = it },
                    onDescriptionChange = { description = it },
                    onCreate = {
                        submittedTitle = title.trim()
                        submittedDescription = description.trim()
                    },
                    onCancel = {}
                )
            }
        }

        composeRule.onNodeWithTag(MobileUiTags.CREATE_TITLE_INPUT).performTextInput("  New queue item  ")
        composeRule.onNodeWithTag(MobileUiTags.CREATE_DESCRIPTION_INPUT).performTextInput("  Needs operator review.  ")
        composeRule.onNodeWithTag(MobileUiTags.CREATE_SUBMIT).performClick()

        assertEquals("New queue item", submittedTitle)
        assertEquals("Needs operator review.", submittedDescription)
    }

    @Test
    fun orderCardClickInvokesSelectionCallback() {
        var selectedOrderId = ""

        composeRule.setContent {
            StatusFlowTheme {
                OrderCard(
                    order = sampleSummary(),
                    isSelected = false,
                    onSelectOrder = { selectedOrderId = it }
                )
            }
        }

        composeRule.onNodeWithTag(MobileUiTags.orderCard("SF-1001")).performClick()

        assertEquals("order-1", selectedOrderId)
    }

    @Test
    fun detailScreenStatusMenuUsesStatusLabels() {
        var transitionedStatus = ""

        composeRule.setContent {
            StatusFlowTheme {
                DetailScreenCard(
                    detail = sampleDetail(),
                    allowedTransitions = listOf("approved", "rejected"),
                    isSubmitting = false,
                    actionMessage = null,
                    selectedTab = MobileDetailTab.Overview,
                    commentBody = "",
                    onCommentBodyChange = {},
                    onSelectTab = {},
                    onTransitionOrder = { transitionedStatus = it },
                    onAddComment = {},
                    isOperator = true
                )
            }
        }

        composeRule.onNodeWithTag(MobileUiTags.DETAIL_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText("Change status").performClick()
        composeRule.onNodeWithText("Approved").performClick()

        assertEquals("approved", transitionedStatus)
    }

    @Test
    fun detailScreenCommentsTabSubmitsOperatorNote() {
        var submittedComment = ""

        composeRule.setContent {
            StatusFlowTheme {
                var commentBody by remember { mutableStateOf("") }
                DetailScreenCard(
                    detail = sampleDetail(),
                    allowedTransitions = listOf("approved"),
                    isSubmitting = false,
                    actionMessage = null,
                    selectedTab = MobileDetailTab.Comments,
                    commentBody = commentBody,
                    onCommentBodyChange = { commentBody = it },
                    onSelectTab = {},
                    onTransitionOrder = {},
                    onAddComment = { submittedComment = commentBody.trim() },
                    isOperator = true
                )
            }
        }

        composeRule.onNodeWithTag(MobileUiTags.COMMENT_INPUT).performTextInput("  Follow up tomorrow.  ")
        composeRule.onNodeWithTag(MobileUiTags.COMMENT_SUBMIT).performClick()

        assertEquals("Follow up tomorrow.", submittedComment)
    }

    @Test
    fun mobileHomeClearsCommentComposerAfterSubmit() {
        var submittedComment = ""

        composeRule.setContent {
            StatusFlowTheme {
                MobileHomeScreen(
                    state = sampleHomeState(),
                    onSignIn = { _, _ -> },
                    onSignOut = {},
                    onRefresh = {},
                    onCreateOrder = { _, _ -> },
                    onSelectOrder = {},
                    onTransitionOrder = {},
                    onAddComment = { submittedComment = it }
                )
            }
        }

        composeRule.onNodeWithText("Comments 1").performClick()
        composeRule.onNodeWithTag(MobileUiTags.COMMENT_INPUT).performTextInput("  Cleared after submit.  ")
        composeRule.onNodeWithTag(MobileUiTags.COMMENT_SUBMIT).performClick()

        assertEquals("Cleared after submit.", submittedComment)
        composeRule.onNodeWithTag(MobileUiTags.COMMENT_SUBMIT).assertIsNotEnabled()
    }

    @Test
    fun fallbackCardsRenderRecoveryStates() {
        composeRule.setContent {
            StatusFlowTheme {
                Column {
                    EmptyQueueCard(
                        title = "No orders match your current view",
                        body = "Try clearing the current filter, editing the search text, or changing the search query to inspect a different slice of the queue.",
                        eyebrow = "FILTERED VIEW",
                        accent = Amber300
                    )
                    DetailUnavailableCard(
                        errorMessage = "Detail lookup timed out.",
                        onBack = {},
                        onRefresh = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag(MobileUiTags.EMPTY_QUEUE).assertIsDisplayed()
        composeRule.onNodeWithText("No orders match your current view").assertIsDisplayed()
        composeRule.onNodeWithTag(MobileUiTags.DETAIL_UNAVAILABLE).assertIsDisplayed()
        composeRule.onNodeWithText("Selected order is unavailable").assertIsDisplayed()
    }

    @Test
    fun loginCardRendersSeededAccounts() {
        composeRule.setContent {
            StatusFlowTheme {
                LoginCard(
                    email = "operator@example.com",
                    password = "operator123",
                    isSubmitting = false,
                    authMessage = null,
                    onEmailChange = {},
                    onPasswordChange = {},
                    onSignIn = {}
                )
            }
        }

        composeRule.onNodeWithTag(MobileUiTags.LOGIN_CARD).assertIsDisplayed()
        composeRule.onNodeWithText("Sign in to the live queue").assertIsDisplayed()
        composeRule.onNodeWithText("operator@example.com / operator123").assertIsDisplayed()
        composeRule.onNodeWithText("customer@example.com / customer123").assertIsDisplayed()
    }

    private fun sampleHomeState(): MobileHomeUiState {
        return MobileHomeUiState(
            isLoading = false,
            session = MobileSessionSummary(
                accessToken = "token",
                email = "operator@example.com",
                name = "Riley Chen",
                role = "operator"
            ),
            orders = listOf(sampleSummary()),
            users = listOf(
                MobileUserSummary(
                    id = "customer-1",
                    email = "customer@example.com",
                    name = "Alex Morgan",
                    role = "customer"
                )
            ),
            selectedOrderId = "order-1",
            selectedOrderDetail = sampleDetail(),
            allowedTransitions = mapOf("in_review" to listOf("approved", "rejected"))
        )
    }

    private fun sampleSummary(): MobileOrderSummary {
        return MobileOrderSummary(
            id = "order-1",
            code = "SF-1001",
            title = "Inspect delivery damage",
            customerName = "Alex Morgan",
            rawStatus = "in_review",
            statusLabel = "In Review",
            statusColor = Color(0xFFFFD574),
            updatedAtLabel = "Apr 10, 2026, 7:45 PM"
        )
    }

    private fun sampleDetail(): MobileOrderDetail {
        return MobileOrderDetail(
            id = "order-1",
            code = "SF-1001",
            title = "Inspect delivery damage",
            description = "Check photos, confirm warranty conditions, and sync findings to the queue.",
            customerName = "Alex Morgan",
            rawStatus = "in_review",
            statusLabel = "In Review",
            statusColor = Color(0xFFFFD574),
            updatedAtLabel = "Apr 10, 2026, 7:45 PM",
            comments = listOf(
                MobileOrderComment(
                    id = "comment-1",
                    body = "Customer attached new photos.",
                    authorName = "Taylor Ops",
                    createdAtLabel = "Apr 10, 2026, 7:40 PM"
                )
            ),
            history = listOf(
                MobileOrderHistoryEvent(
                    id = "history-1",
                    summary = "New -> In Review",
                    reason = "Queue picked up by operator.",
                    actorName = "Taylor Ops",
                    changedAtLabel = "Apr 10, 2026, 7:35 PM"
                )
            )
        )
    }
}
