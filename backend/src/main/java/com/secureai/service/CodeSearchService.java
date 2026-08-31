package com.secureai.service;

import com.secureai.dto.CodeSearchResponse;
import com.secureai.dto.CodeSearchResultItem;
import com.secureai.dto.RepositoryRecordResponse;
import com.secureai.model.AnalysisJob;
import com.secureai.model.CodeIndexEntry;
import com.secureai.model.RepositoryRecord;
import com.secureai.repository.AnalysisJobRepository;
import com.secureai.repository.CodeIndexEntryRepository;
import com.secureai.repository.RepositoryRecordRepository;
import com.secureai.search.Bm25SearchEngine;
import com.secureai.search.Bm25SearchEngine.SearchHit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CodeSearchService {

    private final CodeIndexEntryRepository indexRepository;
    private final AnalysisJobRepository jobRepository;
    private final RepositoryRecordRepository repositoryRecordRepository;
    private final CurrentUserService currentUserService;
    private final Bm25SearchEngine searchEngine;

    @Transactional(readOnly = true)
    public CodeSearchResponse search(String jobId, String query, int limit) {
        Long userId = currentUserService.requireCurrentUser().getId();
        AnalysisJob job = jobRepository.findByJobIdAndUserId(jobId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Analysis job not found"));

        List<CodeIndexEntry> entries = indexRepository.findByJobId(jobId);
        List<SearchHit> hits = searchEngine.search(entries, query, Math.min(limit, 25));

        List<CodeSearchResultItem> results = hits.stream()
                .map(hit -> new CodeSearchResultItem(
                        hit.entry().getFilePath(),
                        hit.entry().getStartLine(),
                        hit.entry().getEndLine(),
                        hit.entry().getLanguage(),
                        truncate(hit.entry().getChunkText(), 1200),
                        round(hit.score())
                ))
                .toList();

        return new CodeSearchResponse(jobId, job.getStatus().name(), query, results.size(), results);
    }

    @Transactional(readOnly = true)
    public RepositoryRecordResponse getRepository(Long id) {
        Long userId = currentUserService.requireCurrentUser().getId();
        RepositoryRecord record = repositoryRecordRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found"));
        return toResponse(record);
    }

    @Transactional(readOnly = true)
    public RepositoryRecordResponse getRepositoryByJobId(String jobId) {
        Long userId = currentUserService.requireCurrentUser().getId();
        jobRepository.findByJobIdAndUserId(jobId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Analysis job not found"));
        RepositoryRecord record = repositoryRecordRepository.findByJobId(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Repository index not found"));
        return toResponse(record);
    }

    private RepositoryRecordResponse toResponse(RepositoryRecord record) {
        return new RepositoryRecordResponse(
                record.getId(),
                record.getJobId(),
                record.getRepositoryUrl(),
                record.getFileCount(),
                record.getIndexedChunkCount(),
                record.getPrimaryLanguage(),
                record.getCreatedAt()
        );
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
