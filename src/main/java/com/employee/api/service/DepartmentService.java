package com.employee.api.service;

import com.employee.api.dto.DepartmentDto;
import com.employee.api.dto.PageResponse;

import java.util.List;

public interface DepartmentService {
    DepartmentDto createDepartment(DepartmentDto departmentDto);

    DepartmentDto getDepartmentById(Long departmentId);

    List<DepartmentDto> getAllDepartments();

    PageResponse<DepartmentDto> getDepartmentsPage(int pageNo, int pageSize, String sortBy, String sortDir);

    DepartmentDto updateDepartment(Long departmentId, DepartmentDto updatedDepartment);

    void deleteDepartment(Long departmentId);
}