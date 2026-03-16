package com.employee.api.service.impl;

import com.employee.api.dto.EmployeeDto;
import com.employee.api.entity.Department;
import com.employee.api.entity.Employee;
import com.employee.api.exception.ResourceNotFoundException;
import com.employee.api.mapper.EmployeeMapper;
import com.employee.api.repository.DepartmentRepository;
import com.employee.api.repository.EmployeeRepository;
import com.employee.api.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.employee.api.service.common.CommonService.getNotFoundExceptionSupplier;

@Service
@Transactional
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;

    @Override
    public EmployeeDto createEmployee(EmployeeDto employeeDto) {
        //입력 받은 DTO => Entity
        Employee employee = EmployeeMapper.mapToEmployee(employeeDto);
        //Department 의 존재여부를 조회
        Department department = departmentRepository.findById(employeeDto.getDepartmentId())
                .orElseThrow(getNotFoundExceptionSupplier(
                        "Department is not exists with id: ",
                                employeeDto.getDepartmentId())
                );
        //Employee 와 Department 연결
        employee.setDepartment(department);
        //Employee 등록
        Employee savedEmployee = employeeRepository.save(employee);
        //DB에 등록된 Entity => DTO
        return EmployeeMapper.mapToEmployeeDto(savedEmployee);
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
