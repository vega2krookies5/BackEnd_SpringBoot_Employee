package com.employee.api.repository;

import com.employee.api.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    // 1. 이메일로 직원 찾기 (unique 제약조건 대응)
    @Query("SELECT e FROM Employee e WHERE e.email = :email")
    Optional<Employee> findByEmail(@Param("email") String email);

    // 2. 성(lastName)이 일치하는 모든 직원 찾기
    List<Employee> findByLastName(String lastName);

    // 3. 이름 또는 성에 특정 문자열이 포함된 직원 검색 (Like 검색)
    List<Employee> findByFirstNameContainingOrLastNameContaining(String firstName, String lastName);

    // 4. 특정 부서 ID에 속한 모든 직원 조회
    List<Employee> findByDepartmentId(Long departmentId);

    // 5. [성능 최적화] 부서 정보까지 한 번에 가져오기 (Fetch Join)
    @Query("SELECT e FROM Employee e JOIN FETCH e.department")
    List<Employee> findAllWithDepartment();
}