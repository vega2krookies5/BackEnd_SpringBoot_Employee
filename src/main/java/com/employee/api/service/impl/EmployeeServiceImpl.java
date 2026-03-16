package com.employee.api.service.impl;

import com.employee.api.dto.EmployeeDto;
import com.employee.api.repository.DepartmentRepository;
import com.employee.api.repository.EmployeeRepository;
import com.employee.api.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {
    private final EmployeeRepository employeeRepository;

    private final DepartmentRepository departmentRepository;

    @Override
    public EmployeeDto createEmployee(EmployeeDto employeeDto) {
        return null;
    }

    @Override
    public EmployeeDto getEmployeeById(Long employeeId) {
        return null;
    }

    @Override
    public List<EmployeeDto> getAllEmployees() {
        return List.of();
    }

    @Override
    public List<EmployeeDto> getAllEmployeesDepartment() {
        return List.of();
    }

    @Override
    public EmployeeDto updateEmployee(Long employeeId, EmployeeDto updatedEmployee) {
        return null;
    }

    @Override
    public void deleteEmployee(Long employeeId) {

    }
}
