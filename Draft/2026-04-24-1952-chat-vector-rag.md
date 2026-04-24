# Chat Vector RAG

## Summary

Completed the minimal P0 chat main-path vector retrieval closure. `ChatService.sendMessage` now tries existing Qdrant-backed vector collections before falling back to the lightweight FULLTEXT/LIKE retrieval path.

## What changed

- Chat replies first search `knowledge_points` vector context, then `ideology_matches` if more context is needed.
- If vector context is found, chat citations are returned with `retrievalStatus=FOUND` and `matchedBy=VECTOR`.
- If vector context is empty, the existing `KnowledgeRetrievalService.retrieveWithStatus` path is preserved.
- Vector context citation item types are normalized to `KNOWLEDGE_POINT`, `IDEOLOGY_MATCH`, or `SELECTION_EXPLAIN` instead of leaking raw payload source types.
- Duplicate vector contexts are removed before truncating to the requested limit.
- Regression tests cover vector-first behavior and fallback behavior.

## Key files

- `backend/src/main/java/com/smartedu/service/ChatService.java`
- `backend/src/test/java/com/smartedu/service/ChatServiceTest.java`
- `backend/src/test/java/com/smartedu/controller/ChatControllerTest.java`

## Review notes

The post-change review found deeper follow-up items that are intentionally not over-expanded in this minimal closure:

- Searching multiple vector collections currently embeds the same query more than once because `VectorIndexService.search` owns embedding internally.
- Vector payloads do not yet carry source title/source URL fields, so vector-first citations can identify hit title/snippet but not a high-fidelity external link.
- Vector hits are currently treated as `FOUND` whenever any result is returned; score thresholds, weak-match semantics, global cross-collection ranking, and reranking remain future work.
- `referenceId` for current parse-task vector payloads still points at parse task id rather than a stable per-item id.

## Verification

- `mvn -f backend/pom.xml -Dtest=ChatServiceTest,ChatControllerTest test` passed.

## Remaining work

- Reuse one embedding for multi-collection chat vector search.
- Add vector hit score thresholds and WEAK_MATCH handling.
- Persist and expose source/sourceUrl metadata in vector payloads.
- Add reranking and RAG automatic evaluation.
- Perform browser/manual validation against a running Qdrant + embedding service.
