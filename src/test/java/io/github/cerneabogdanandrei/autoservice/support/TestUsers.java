package io.github.cerneabogdanandrei.autoservice.support;

/**
 * Development users created by {@code db/seed/R__dev_seed_users.sql}.
 */
public enum TestUsers {

    ADMIN("admin", "Admin123!"),
    OWNER("owner", "Owner123!"),
    EMPLOYEE("employee", "Employee123!");

    private final String username;
    private final String password;

    TestUsers(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }
}
