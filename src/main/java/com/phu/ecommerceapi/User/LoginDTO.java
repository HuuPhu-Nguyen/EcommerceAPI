package com.phu.ecommerceapi.User;

import lombok.Data;
import lombok.Getter;

@Data
@Getter
public class LoginDTO {
    private String username;
    private String password;
}
