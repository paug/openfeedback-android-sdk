package io.openfeedback.android.sources

import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import io.openfeedback.android.mappers.convertToModel
import io.openfeedback.android.model.Project
import io.openfeedback.android.model.SessionVotes
import io.openfeedback.android.model.UserVote
import io.openfeedback.android.model.VoteStatus
import io.openfeedback.android.toFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class OpenFeedbackFirestore(
    private val firestore: FirebaseFirestore
) {
    fun project(projectId: String): Flow<Project> =
        firestore.collection("projects")
            .document(projectId)
            .toFlow()
            .map { querySnapshot ->
                querySnapshot.toObject(Project::class.java)!!
            }

    fun userVotes(projectId: String, userId: String, sessionId: String): Flow<List<UserVote>> =
        firestore.collection("projects/$projectId/userVotes")
            .whereEqualTo("userId", userId)
            .whereEqualTo("status", VoteStatus.Active.value)
            .whereEqualTo("talkId", sessionId)
            .toFlow()
            .map { querySnapshot ->
                querySnapshot.map {
                    UserVote(
                        voteItemId = it.data["voteItemId"] as String,
                        voteId = it.data["voteId"] as String?
                    )
                }
            }

    fun sessionVotes(projectId: String, sessionId: String): Flow<SessionVotes> =
        firestore.collection("projects/$projectId/sessionVotes")
            .document(sessionId)
            .toFlow()
            .map { querySnapshot ->
                SessionVotes(
                    votes = querySnapshot.data
                        ?.filter { it.value is Long } as? Map<String, Long>
                        ?: emptyMap(), // If there's no vote yet, default to an empty map }
                    comments = querySnapshot.data
                        ?.filter { it.value is HashMap<*, *> }
                        ?.map {
                            val voteItemId = it.key
                            (it.value as HashMap<*, *>).entries
                                .filter { (it.value as Map<String, *>).isNotEmpty() }
                                .map { entry ->
                                    entry.key as String to (entry.value as Map<String, *>)
                                        .convertToModel(
                                            id = entry.key as String,
                                            voteItemId = voteItemId
                                        )
                                }
                        }
                        ?.flatten()
                        ?.associate { it.first to it.second }
                        ?: emptyMap()
                )
            }

    suspend fun newComment(
        projectId: String,
        userId: String,
        talkId: String,
        voteItemId: String,
        status: VoteStatus,
        text: String
    ) {
        if (text.trim() == "") return
        val collectionReference = firestore.collection("projects/$projectId/userVotes")
        val querySnapshot = collectionReference
            .whereEqualTo("userId", userId)
            .whereEqualTo("talkId", talkId)
            .whereEqualTo("voteItemId", voteItemId)
            .get()
            .await()
        if (querySnapshot.isEmpty) {
            val documentReference = collectionReference.document()
            documentReference.set(
                mapOf(
                    "id" to documentReference.id,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "projectId" to projectId,
                    "status" to status.value,
                    "talkId" to talkId,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "userId" to userId,
                    "voteItemId" to voteItemId,
                    "text" to text.trim()
                )
            )
        } else {
            collectionReference
                .document(querySnapshot.documents[0].id)
                .update(
                    mapOf(
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "status" to status.value,
                        "text" to text.trim()
                    )
                )
        }
    }

    suspend fun setVote(
        projectId: String,
        userId: String,
        talkId: String,
        voteItemId: String,
        status: VoteStatus
    ) {
        val collectionReference = firestore.collection("projects/$projectId/userVotes")
        val querySnapshot = collectionReference
            .whereEqualTo("userId", userId)
            .whereEqualTo("talkId", talkId)
            .whereEqualTo("voteItemId", voteItemId)
            .get()
            .await()
        if (querySnapshot.isEmpty) {
            val documentReference = collectionReference.document()
            documentReference.set(
                mapOf(
                    "id" to documentReference.id,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "projectId" to projectId,
                    "status" to status.value,
                    "talkId" to talkId,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "userId" to userId,
                    "voteItemId" to voteItemId
                )
            )
        } else {
            collectionReference
                .document(querySnapshot.documents[0].id)
                .update(
                    mapOf(
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "status" to status.value
                    )
                )
        }
    }

    suspend fun upVote(
        projectId: String,
        userId: String,
        talkId: String,
        voteItemId: String,
        voteId: String,
        status: VoteStatus
    ) {
        val collectionReference = firestore.collection("projects/$projectId/userVotes")
        val querySnapshot = collectionReference
            .whereEqualTo("userId", userId)
            .whereEqualTo("talkId", talkId)
            .whereEqualTo("voteItemId", voteItemId)
            .whereEqualTo("voteId", voteId)
            .get()
            .await()
        if (querySnapshot.isEmpty) {
            val documentReference = collectionReference.document()
            documentReference.set(
                mapOf(
                    "projectId" to projectId,
                    "talkId" to talkId,
                    "voteItemId" to voteItemId,
                    "id" to documentReference.id,
                    "voteId" to voteId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "voteType" to "textPlus",
                    "userId" to userId,
                    "status" to status.value
                )
            )
        } else {
            collectionReference
                .document(querySnapshot.documents[0].id)
                .update(
                    mapOf(
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "status" to status.value
                    )
                )
        }
    }

    companion object Factory {
        fun create(app: FirebaseApp): OpenFeedbackFirestore {
            val firestore = FirebaseFirestore.getInstance(app)
            firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build()
            return OpenFeedbackFirestore(firestore)
        }
    }
}
