DROP DATABASE IF EXISTS sample;
CREATE DATABASE sample CHARACTER SET = ucs2;

DROP USER 'sample';
CREATE USER 'sample' IDENTIFIED by 'password';
GRANT ALL ON sample.* to 'sample';
GRANT ALL ON mysql.proc to 'sample';

CREATE TABLE sample.employee(
	id BIGINT(19) UNSIGNED NOT NULL AUTO_INCREMENT,
    birth_date  CHAR(8)            NOT NULL,
    first_name  VARCHAR(32)     NOT NULL,
    last_name   VARCHAR(32)     NOT NULL,
    gender      CHAR(1)  NOT NULL,
    hire_date   VARCHAR(8)      NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE sample.department (
    id BIGINT(19) UNSIGNED NOT NULL AUTO_INCREMENT,
    dept_name   VARCHAR(40)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE  KEY (dept_name)
);

CREATE TABLE sample.dept_manager (
   id BIGINT(19) UNSIGNED NOT NULL,
   dept_id      BIGINT(19) UNSIGNED NOT NULL,
   from_date    VARCHAR(8)      NOT NULL,
   to_date      VARCHAR(8)      NOT NULL,
   FOREIGN KEY (id)  REFERENCES sample.employee(id)    ON DELETE CASCADE,
   FOREIGN KEY (dept_id) REFERENCES sample.department(id) ON DELETE CASCADE,
   PRIMARY KEY (id)
);

CREATE TABLE sample.dept_emp (
    emp_id      BIGINT(19) UNSIGNED    NOT NULL,
    dept_id     BIGINT(19) UNSIGNED      NOT NULL,
    FOREIGN KEY (emp_id)  REFERENCES sample.employee(id) ON DELETE CASCADE,
    FOREIGN KEY (dept_id) REFERENCES sample.department (id) ON DELETE CASCADE,
    PRIMARY KEY (emp_id,dept_id)
);
