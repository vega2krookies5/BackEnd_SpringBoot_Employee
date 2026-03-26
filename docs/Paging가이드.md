# Spring Data JPA 페이징(Paging) 기능 구현 및 React 연동 가이드

## 1. 개요

Spring Data Core의 `Pageable` / `Page` / `Sort` 인터페이스와
Spring Data JPA의 `JpaRepository.findAll(Pageable)` 메서드를 활용하여
Employee 및 Department 목록 조회에 페이징·정렬 기능을 추가했습니다.

---

## 2. 핵심 Spring Data 클래스/인터페이스

| 클래스/인터페이스 | 역할 |
|---|---|
| `org.springframework.data.domain.Pageable` | 페이지 번호·크기·정렬 정보를 담는 인터페이스 |
| `org.springframework.data.domain.PageRequest` | `Pageable`의 구현체, `PageRequest.of(page, size, sort)`로 생성 |
| `org.springframework.data.domain.Sort` | 정렬 방향(ASC/DESC)과 기준 컬럼을 표현 |
| `org.springframework.data.domain.Page<T>` | 조회 결과 페이지 데이터 + 페이지 메타정보를 포함하는 인터페이스 |
| `JpaRepository.findAll(Pageable)` | Pageable을 받아 Page\<T\>를 반환 (Repository에 추가 코드 없이 사용 가능) |

---

## 3. 추가/수정된 파일 목록

| 파일 | 변경 내용 |
|---|---|
| `dto/PageResponse.java` | **신규 생성** - 페이징 결과 래퍼 DTO |
| `service/EmployeeService.java` | `getEmployeesPage()` 메서드 추가 |
| `service/DepartmentService.java` | `getDepartmentsPage()` 메서드 추가 |
| `service/impl/EmployeeServiceImpl.java` | `getEmployeesPage()` 구현 |
| `service/impl/DepartmentServiceImpl.java` | `getDepartmentsPage()` 구현 |
| `controller/EmployeeController.java` | `GET /api/employees/page` 엔드포인트 추가 |
| `controller/DepartmentController.java` | `GET /api/departments/page` 엔드포인트 추가 |
| `db/.../V3__insert_more_data.sql` | 부서 5개·직원 20명 추가 (테스트 데이터) |

---

## 4. PageResponse\<T\> DTO (응답 구조)

```java
public class PageResponse<T> {
    private List<T> content;       // 현재 페이지 데이터 목록
    private int pageNo;            // 현재 페이지 번호 (0부터 시작)
    private int pageSize;          // 페이지당 데이터 수
    private long totalElements;    // 전체 데이터 수
    private int totalPages;        // 전체 페이지 수
    private boolean last;          // 마지막 페이지 여부
}
```

---

## 5. 서비스 구현 흐름

```
컨트롤러 파라미터 (pageNo, pageSize, sortBy, sortDir)
  │
  ▼
Sort sort = sortDir.equalsIgnoreCase("asc")
    ? Sort.by(sortBy).ascending()
    : Sort.by(sortBy).descending();
  │
  ▼
Pageable pageable = PageRequest.of(pageNo, pageSize, sort);
  │
  ▼
Page<Employee> page = employeeRepository.findAll(pageable);
  │   ┌─────────────────────────────────┐
  │   │ page.getContent()     → 현재 페이지 데이터 목록  │
  │   │ page.getNumber()      → pageNo                │
  │   │ page.getSize()        → pageSize              │
  │   │ page.getTotalElements()→ totalElements        │
  │   │ page.getTotalPages()  → totalPages            │
  │   │ page.isLast()         → last                  │
  │   └─────────────────────────────────┘
  ▼
PageResponse<EmployeeDto> 반환
```

---

## 6. API 명세

### 6-1. 직원 페이징 조회

```
GET /api/employees/page
```

| 쿼리 파라미터 | 타입 | 기본값 | 설명 |
|---|---|---|---|
| `pageNo` | int | 0 | 페이지 번호 (0부터 시작) |
| `pageSize` | int | 10 | 페이지당 데이터 수 |
| `sortBy` | String | id | 정렬 기준 컬럼: `id`, `firstName`, `lastName`, `email` |
| `sortDir` | String | asc | 정렬 방향: `asc` / `desc` |

### 6-2. 부서 페이징 조회

```
GET /api/departments/page
```

| 쿼리 파라미터 | 타입 | 기본값 | 설명 |
|---|---|---|---|
| `pageNo` | int | 0 | 페이지 번호 (0부터 시작) |
| `pageSize` | int | 10 | 페이지당 데이터 수 |
| `sortBy` | String | id | 정렬 기준 컬럼: `id`, `departmentName`, `departmentDescription` |
| `sortDir` | String | asc | 정렬 방향: `asc` / `desc` |

---

## 7. 실제 요청/응답 예시 (V2 + V3 데이터 기준)

### 요청
```
GET http://localhost:8080/api/employees/page?pageNo=0&pageSize=5&sortBy=firstName&sortDir=asc
```

### 응답 JSON
```json
{
  "content": [
    { "id": 20, "firstName": "Amelia",    "lastName": "Wright",   "email": "amelia@company.com",    "departmentId": 4, "departmentDto": null },
    { "id": 12, "firstName": "Ava",       "lastName": "Lee",      "email": "ava@company.com",       "departmentId": 4, "departmentDto": null },
    { "id": 18, "firstName": "Charlotte", "lastName": "Young",    "email": "charlotte@company.com", "departmentId": 2, "departmentDto": null },
    { "id": 7,  "firstName": "Daniel",    "lastName": "Martinez", "email": "daniel@company.com",    "departmentId": 3, "departmentDto": null },
    { "id": 10, "firstName": "Emma",      "lastName": "Martin",   "email": "emma@company.com",      "departmentId": 2, "departmentDto": null }
  ],
  "pageNo": 0,
  "pageSize": 5,
  "totalElements": 23,
  "totalPages": 5,
  "last": false
}
```

> **React에서 사용할 핵심 필드**
> - `content` → 테이블/목록에 렌더링할 데이터 배열
> - `totalPages` → 페이지 번호 버튼 개수 계산
> - `pageNo` → 현재 선택된 페이지 표시
> - `last` → "다음" 버튼 비활성화 여부

---

## 8. React 연동 방법

### 8-1. 페이징 상태 설계

React 컴포넌트에서 관리해야 할 상태입니다.

```jsx
const [employees, setEmployees]       = useState([]);   // content 배열
const [currentPage, setCurrentPage]   = useState(0);    // pageNo (0부터 시작)
const [pageSize]                      = useState(5);     // 페이지당 항목 수
const [totalPages, setTotalPages]     = useState(0);     // 전체 페이지 수
const [sortBy, setSortBy]             = useState('id');  // 정렬 컬럼
const [sortDir, setSortDir]           = useState('asc'); // 정렬 방향
```

### 8-2. API 호출 (axios 사용)

```jsx
useEffect(() => {
    const fetchEmployees = async () => {
        const response = await axios.get('http://localhost:8080/api/employees/page', {
            params: {
                pageNo:   currentPage,
                pageSize: pageSize,
                sortBy:   sortBy,
                sortDir:  sortDir
            }
        });
        setEmployees(response.data.content);
        setTotalPages(response.data.totalPages);
    };
    fetchEmployees();
}, [currentPage, sortBy, sortDir]);  // 의존성: 페이지·정렬 변경 시 재호출
```

> `pageNo`는 서버에서 **0부터 시작**하므로 `currentPage` 초기값을 `0`으로 설정합니다.
> UI에서 "1페이지"로 표시하려면 `currentPage + 1`로 렌더링합니다.

### 8-3. 페이지 이동 핸들러

```jsx
// 이전 페이지
const handlePrevPage = () => {
    if (currentPage > 0) setCurrentPage(prev => prev - 1);
};

// 다음 페이지
const handleNextPage = () => {
    if (currentPage < totalPages - 1) setCurrentPage(prev => prev + 1);
};

// 특정 페이지로 이동
const handlePageClick = (pageNum) => {
    setCurrentPage(pageNum);
};
```

### 8-4. 정렬 변경 핸들러

```jsx
const handleSort = (column) => {
    if (sortBy === column) {
        // 같은 컬럼 클릭 시 방향 전환
        setSortDir(prev => (prev === 'asc' ? 'desc' : 'asc'));
    } else {
        setSortBy(column);
        setSortDir('asc');
    }
    setCurrentPage(0); // 정렬 변경 시 첫 페이지로 이동
};
```

### 8-5. 페이지 번호 버튼 렌더링

```jsx
{/* 페이지 번호 버튼 */}
<div>
    <button onClick={handlePrevPage} disabled={currentPage === 0}>
        이전
    </button>

    {[...Array(totalPages)].map((_, index) => (
        <button
            key={index}
            onClick={() => handlePageClick(index)}
            style={{ fontWeight: currentPage === index ? 'bold' : 'normal' }}
        >
            {index + 1}
        </button>
    ))}

    <button onClick={handleNextPage} disabled={currentPage === totalPages - 1}>
        다음
    </button>
</div>
```

---

## 9. 클래스 관계 요약

```
[React 클라이언트]
  │  GET /api/employees/page?pageNo=0&pageSize=5&sortBy=firstName&sortDir=asc
  ▼
EmployeeController.getEmployeesPage(pageNo, pageSize, sortBy, sortDir)
  ▼
EmployeeServiceImpl.getEmployeesPage()
  ├─ Sort.by(sortBy).ascending()           ← sortDir=asc
  ├─ PageRequest.of(pageNo, pageSize, sort) → Pageable
  ├─ EmployeeRepository.findAll(pageable)  → Page<Employee>
  └─ Page<Employee> → List<EmployeeDto> → PageResponse<EmployeeDto>
  ▼
[JSON 응답] { content:[...], pageNo, pageSize, totalElements, totalPages, last }
  ▼
[React 클라이언트]
  ├─ content       → 테이블 행 렌더링
  ├─ totalPages    → 페이지 버튼 생성
  ├─ pageNo        → 현재 페이지 하이라이트
  └─ last          → 다음 버튼 disabled 처리
```

---

## 10. 주의사항

| 항목 | 내용                                                                                                               |
|---|------------------------------------------------------------------------------------------------------------------|
| pageNo 시작값 | Spring Data는 **0부터 시작**. React `currentPage` 초기값을 `0`으로 설정                                                       |
| UI 표시 | 사용자에게는 `currentPage + 1`로 표시 (1부터 시작하는 것처럼 보이게)                                                                  |
| sortBy 컬럼명 | Entity 필드명 기준 (`firstName`, `departmentName` 등). DB 컬럼명(`first_name`)이 아님                                        |
| 잘못된 sortBy | 존재하지 않는 필드명 입력 시 서버에서 `PropertyReferenceException` 발생 → 클라이언트에서 허용 컬럼 목록으로 제한 권장                                 |
| LAZY 로딩 | `GET /api/employees/page` 응답의 `departmentDto`는 항상 `null`. 부서 정보가 필요하면 `GET /api/employees/departments`(전체 목록) 사용 |
| 정렬 변경 시 | 정렬 기준/방향이 바뀌면 반드시 `currentPage`를 `0`으로 초기화 해야 의도한 결과가 나옴                                                         |
