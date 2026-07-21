package com.secureai.service;

import com.secureai.dto.ProjectRequest;
import com.secureai.dto.ProjectResponse;
import com.secureai.model.Project;
import com.secureai.model.User;
import com.secureai.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final CurrentUserService currentUserService;

    @Transactional
    public ProjectResponse create(ProjectRequest request) {
        User user = currentUserService.requireCurrentUser();
        Project project = Project.builder()
                .user(user)
                .name(request.name())
                .repositoryUrl(request.repositoryUrl())
                .build();
        return toResponse(projectRepository.save(project));
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> listMine() {
        User user = currentUserService.requireCurrentUser();
        return projectRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getMine(Long id) {
        User user = currentUserService.requireCurrentUser();
        Project project = projectRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        return toResponse(project);
    }

    public Project requireOwnedProject(Long id) {
        User user = currentUserService.requireCurrentUser();
        return projectRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
    }

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getRepositoryUrl(),
                project.getCreatedAt()
        );
    }
}
