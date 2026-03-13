package com.employee.api.mapper;

import com.employee.api.dto.EmployeeDto;
import com.employee.api.entity.Employee;

public class EmployeeMapper {
    // Entity -> DTO (ID만 포함)
    public static EmployeeDto mapToEmployeeDto(Employee employee){
        return EmployeeDto.builder()
                .id(employee.getId())
                .firstName(employee.getFirstName())
                .lastName(employee.getLastName())
                .email(employee.getEmail())
                .departmentId(employee.getDepartment().getId())
                .build();
    }

    // Entity -> DTO (전체 부서 정보 포함)
    public static EmployeeDto mapToEmployeeDepartmentDto(Employee employee){
        return EmployeeDto.builder()
                .id(employee.getId())
                .firstName(employee.getFirstName())
                .lastName(employee.getLastName())
                .email(employee.getEmail())
                .departmentDto(DepartmentMapper.mapToDepartmentDto(employee.getDepartment()))
                .build();
    }

    // DTO -> Entity
    public static Employee mapToEmployee(EmployeeDto employeeDto){
        // 엔티티에 Setter를 사용하는 기존 방식보다 빌더가 있다면 빌더 사용을 권장합니다.
        Employee employee = new Employee();
        employee.setId(employeeDto.getId());
        employee.setFirstName(employeeDto.getFirstName());
        employee.setLastName(employeeDto.getLastName());
        employee.setEmail(employeeDto.getEmail());
        // 필요 시 부서 세팅 로직 추가 가능
        return employee;
    }

}