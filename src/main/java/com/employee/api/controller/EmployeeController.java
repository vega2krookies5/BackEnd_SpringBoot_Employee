package com.employee.api.controller;

import com.employee.api.dto.EmployeeDto;
import com.employee.api.dto.PageResponse;
import com.employee.api.entity.Employee;
import com.employee.api.repository.EmployeeRepository;
import com.employee.api.service.EmployeeService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;
    private final EmployeeRepository employeeRepository;

    @GetMapping("/welcome")
    public String welcome() {
        return "Welcome this endpoint is not secure";
    }

    // Build Add Employee REST API
    @PostMapping
    public ResponseEntity<EmployeeDto> createEmployee(@Valid @RequestBody EmployeeDto employeeDto){
        EmployeeDto savedEmployee = employeeService.createEmployee(employeeDto);
        return new ResponseEntity<>(savedEmployee, HttpStatus.CREATED);
    }

    // Build Get Employee REST API
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<EmployeeDto> getEmployeeById(@PathVariable("id") Long employeeId){
        EmployeeDto employeeDto = employeeService.getEmployeeById(employeeId);
        return ResponseEntity.ok(employeeDto);
    }

    // Build Get All Employees REST API
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<EmployeeDto>> getAllEmployees(){
        List<EmployeeDto> employees = employeeService.getAllEmployees();
        return ResponseEntity.ok(employees);
    }

    /*  Entity를 입출력 타입으로 사용했을 경우에
        Hibernate가 Proxy 가짜객체를 Json으로 변환하지 못할때 발생하는 예외입니다.
       "Type definition error: [simple type, class org.hibernate.proxy.pojo.bytebuddy.ByteBuddyInterceptor]
     */
    @GetMapping("/bad")
    public ResponseEntity<List<Employee>> getAllEmployeesBad(){
        //List<EmployeeDto> employees = employeeService.getAllEmployees();
        List<Employee> employees = employeeRepository.findAll();
        return ResponseEntity.ok(employees);
    }

    @GetMapping("/departments")
    public ResponseEntity<List<EmployeeDto>> getAllEmployeesDepartment() {
        return ResponseEntity.ok(employeeService.getAllEmployeesDepartment());
    }


    // Build Update Employee REST API
    @PutMapping("/{id}")
    public ResponseEntity<EmployeeDto> updateEmployee(@PathVariable("id") Long employeeId,
                                                      @Valid @RequestBody EmployeeDto updatedEmployee){
          EmployeeDto employeeDto = employeeService.updateEmployee(employeeId, updatedEmployee);
          return ResponseEntity.ok(employeeDto);
    }

    // Build Delete Employee REST API
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteEmployee(@PathVariable("id") Long employeeId){
        employeeService.deleteEmployee(employeeId);
        return ResponseEntity.ok("Employee deleted successfully!.");
    }

    @GetMapping("/email/{email}")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<EmployeeDto> getEmployeeByEmail(@PathVariable String email){
        EmployeeDto employeeDto = employeeService.getEmployeeByEmail(email);
        return ResponseEntity.ok(employeeDto);
    }

    //http://localhost:8080/api/employees/page?pageNo=1&pageSize=5&sortBy=id&sortDir=asc
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<PageResponse<EmployeeDto>> getEmployeesPage(
            @RequestParam(defaultValue = "0") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir
    ) {
        return ResponseEntity.ok(employeeService.getEmployeesPage(pageNo, pageSize, sortBy, sortDir));
    }
}
