package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentOptionDto {
    private Long id;
    private String username;
    private String realName;
    private String email;
    private String department;
}
