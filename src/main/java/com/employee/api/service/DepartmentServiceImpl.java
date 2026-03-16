package com.employee.api.service;

import com.employee.api.dto.DepartmentDto;
import com.employee.api.entity.Department;
import com.employee.api.exception.ResourceNotFoundException;
import com.employee.api.mapper.DepartmentMapper;
import com.employee.api.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Supplier;

@Service
@Transactional
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService{
    private final DepartmentRepository departmentRepository;

    @Override
    public DepartmentDto createDepartment(DepartmentDto departmentDto) {
        //DTO => Entity 변환
        Department department = DepartmentMapper.mapToDepartment(departmentDto);
        //등록 처리
        Department savedDepartment = departmentRepository.save(department);
        //등록된 Entity => DTO 변환
        return DepartmentMapper.mapToDepartmentDto(savedDepartment);
    }

    @Transactional(readOnly = true)
    @Override
    public DepartmentDto getDepartmentById(Long departmentId) {
        /*
         Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Department is not exists with a given id: " + departmentId)
        );
        //Entity => DTO 변환
        return DepartmentMapper.mapToDepartmentDto(department);
         */
        return departmentRepository.findById(departmentId) //Optional<Department>
                //.map(department -> DepartmentMapper.mapToDepartmentDto(department))
                .map(DepartmentMapper::mapToDepartmentDto) //Optional<DepartmentDto)
                .orElseThrow(getNotFoundExceptionSupplier(
                        "Department is not exists with a given id: ", departmentId)
                );
    }

    //public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier)
    //Supplier의 추상메서드  T get()
    private static Supplier<ResourceNotFoundException> getNotFoundExceptionSupplier(String msg,
                                                                                    Long departmentId) {
        return () -> new ResourceNotFoundException(msg + departmentId, HttpStatus.NOT_FOUND);
    }

    @Transactional(readOnly = true)
    @Override
    public List<DepartmentDto> getAllDepartments() {
        List<Department> departments = departmentRepository.findAll();
        // List<Department> => Stream<Department>
        return departments.stream() //Stream<Department>
                //.map(department -> DepartmentMapper.mapToDepartmentDto(department)) //Stream<DepartmentDto>
                .map(DepartmentMapper::mapToDepartmentDto)  //Stream<DepartmentDto>
        // Stream<DepartmentDto> => List<DepartmentDto>
                .toList();
    }

    @Override
    public DepartmentDto updateDepartment(Long departmentId, DepartmentDto updatedDepartment) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(getNotFoundExceptionSupplier(
                        "Department is not exists with a given id:", departmentId)
                );
        //setter 호출
        department.setDepartmentName(updatedDepartment.getDepartmentName());
        department.setDepartmentDescription(updatedDepartment.getDepartmentDescription());

        //Department savedDepartment = departmentRepository.save(department);

        //Entity => DTO 로 변환
        return DepartmentMapper.mapToDepartmentDto(department);
    }

    @Override
    public void deleteDepartment(Long departmentId) {

    }
}
