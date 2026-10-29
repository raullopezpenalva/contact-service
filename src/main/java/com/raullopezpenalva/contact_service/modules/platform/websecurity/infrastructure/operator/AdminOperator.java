package com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.operator;

import java.io.*;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.application.AdminAccountService;

/** One-shot operator entry; never starts the application, HTTP routes or notification workers. */
public final class AdminOperator {
    private AdminOperator() {}
    public record Command(String operation,String login,Path passwordFile) {}

    public static Command parse(String[] args) {
        if(args.length==2 && args[0].equals("baseline-legacy") && args[1].equals("--confirm-offline"))
            return new Command("baseline-legacy",null,null);
        if(args.length<3 || !Set.of("provision","reset-password","disable","enable").contains(args[0]) || !args[1].equals("--login"))
            throw new IllegalArgumentException("Invalid operator command");
        boolean password=Set.of("provision","reset-password").contains(args[0]);
        if(args.length!=3 && !(password && args.length==5 && args[3].equals("--password-file")))
            throw new IllegalArgumentException("Unexpected operator arguments");
        String login=com.raullopezpenalva.contact_service.modules.platform.websecurity.domain.AdminAccount.canonicalLogin(args[2]);
        return new Command(args[0],login,args.length==5?Path.of(args[4]):null);
    }

    public static char[] readPassword(Path file) throws IOException {
        if(file==null) {
            Console console=System.console();
            if(console==null) throw new IllegalArgumentException("Use a private password file or an interactive console");
            char[] value=console.readPassword("New administrator password: ");
            if(value==null) throw new IllegalArgumentException("Password input cancelled");
            return value;
        }
        if(Files.isSymbolicLink(file) || !Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException("Password file must be a regular private file");
        var permissions=Files.getPosixFilePermissions(file,LinkOption.NOFOLLOW_LINKS);
        if(!permissions.contains(PosixFilePermission.OWNER_READ) || permissions.stream()
            .anyMatch(p -> p!=PosixFilePermission.OWNER_READ && p!=PosixFilePermission.OWNER_WRITE))
            throw new IllegalArgumentException("Password file permissions must be 400 or 600");
        byte[] bytes;
        try(var in=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)) {bytes=in.readNBytes(515);}
        try {
            if(bytes.length>514) throw new IllegalArgumentException("Password input too large");
            var decoded=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes));
            char[] value=new char[decoded.remaining()];decoded.get(value);
            if(decoded.hasArray())Arrays.fill(decoded.array(),'\0');
            // A file may have one terminal newline; spaces are part of the password.
            int n=value.length;
            if(n>0 && value[n-1]=='\n') {n--;if(n>0 && value[n-1]=='\r')n--;}
            char[] result=Arrays.copyOf(value,n);Arrays.fill(value,'\0');return result;
        } finally {Arrays.fill(bytes,(byte)0);}
    }

    public static DataSource dataSource(Map<String,String> env) {
        String url=env.get("SPRING_DATASOURCE_URL");
        String username=env.get("SPRING_DATASOURCE_USERNAME");
        String password=env.get("SPRING_DATASOURCE_PASSWORD");
        if(url==null || username==null || password==null || !url.startsWith("jdbc:postgresql://"))
            throw new IllegalArgumentException("Explicit PostgreSQL connection configuration is required");
        URI uri=URI.create(url.substring(5));
        String query=uri.getRawQuery();
        boolean inlineCredentials=query!=null && Arrays.stream(query.split("&"))
            .map(part->URLDecoder.decode(part.split("=",2)[0],StandardCharsets.UTF_8).toLowerCase(Locale.ROOT))
            .anyMatch(Set.of("user","password","passfile","sslpassword")::contains);
        if(uri.getRawUserInfo()!=null || inlineCredentials)
            throw new IllegalArgumentException("Credentials must be separate from the JDBC URL");
        return new DriverManagerDataSource(url,username,password);
    }

    public static int run(String[] args) {
        char[] password=null;
        try {
            Command command=parse(args);
            DataSource source=dataSource(System.getenv());
            if(command.operation().equals("baseline-legacy")) {
                LegacySchemaBaseline.baseline(source);
                System.out.println("Verified legacy schema baseline recorded; run the reviewed migrations next");
                return 0;
            }
            if(Set.of("provision","reset-password").contains(command.operation())) password=readPassword(command.passwordFile());
            try(var context=new SpringApplicationBuilder(AdminOperatorConfiguration.class)
                .profiles("admin-operator-internal").web(WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off","logging.level.org.flywaydb=warn").run()) {
                var service=context.getBean(AdminAccountService.class);
                var result=switch(command.operation()) {
                    case "provision" -> service.provision(command.login(),password);
                    case "reset-password" -> service.resetPassword(command.login(),password);
                    case "disable" -> service.setEnabled(command.login(),false);
                    case "enable" -> service.setEnabled(command.login(),true);
                    default -> throw new IllegalArgumentException("Invalid operator command");
                };
                System.out.println("Operator operation completed; accountId="+result.id());
            }
            return 0;
        } catch(Exception ex) {
            // Underlying JDBC/argument/IO messages may include credentials or file contents.
            System.err.println("Operator operation refused; verify command, private input, schema and last-admin safeguards; errorType="+ex.getClass().getSimpleName());
            return 2;
        } finally {if(password!=null)Arrays.fill(password,'\0');}
    }
}
