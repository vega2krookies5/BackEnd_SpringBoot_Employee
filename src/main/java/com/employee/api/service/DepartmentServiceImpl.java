package com.employee.api.service;

import com.employee.api.dto.DepartmentDto;
import com.employee.api.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService{
    private final DepartmentRepository departmentRepository;

    @Override
    public DepartmentDto createDepartment(DepartmentDto departmentDto) {
        return null;
    }

    @Override
    public DepartmentDto getDepartmentById(Long departmentId) {
        return null;
    }

    @Override
    public List<DepartmentDto> getAllDepartments() {
        return List.of();
    }

    @Override
    public DepartmentDto updateDepartment(Long departmentId, DepartmentDto updatedDepartment) {
        return null;
    }

    @Override
    public void deleteDepartment(Long departmentId) {

    }
}
