package com.nimba.review.internal

import com.nimba.shared.CurrentUser
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/credit-cases/{caseId}/review")
class ReviewController(
    private val reviewService: ReviewService,
    private val currentUser: CurrentUser,
) {
    @GetMapping
    fun overview(
        @PathVariable caseId: UUID,
    ): ReviewOverviewResponse = reviewService.overview(caseId, currentUser.id())

    @PostMapping("/comments")
    @ResponseStatus(HttpStatus.CREATED)
    fun addComment(
        @PathVariable caseId: UUID,
        @Valid @RequestBody request: ReviewCommentRequest,
    ): ReviewThreadResponse = reviewService.addComment(caseId, currentUser.id(), request.sectionKey, request.body, request.parentId)

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePendingComment(
        @PathVariable caseId: UUID,
        @PathVariable commentId: UUID,
    ) = reviewService.deletePendingComment(caseId, currentUser.id(), commentId)

    @PostMapping("/comments/{commentId}/resolve")
    fun resolve(
        @PathVariable caseId: UUID,
        @PathVariable commentId: UUID,
    ): ReviewThreadResponse = reviewService.resolve(caseId, currentUser.id(), commentId, resolved = true)

    @PostMapping("/comments/{commentId}/unresolve")
    fun unresolve(
        @PathVariable caseId: UUID,
        @PathVariable commentId: UUID,
    ): ReviewThreadResponse = reviewService.resolve(caseId, currentUser.id(), commentId, resolved = false)

    /** Submits the caller's draft review: verdict + summary → the workflow transition fires atomically. */
    @PostMapping("/submit")
    fun submit(
        @PathVariable caseId: UUID,
        @Valid @RequestBody request: ReviewSubmitRequest,
    ): ReviewResponse = reviewService.submitReview(caseId, currentUser.id(), request.verdict, request.summary)
}
