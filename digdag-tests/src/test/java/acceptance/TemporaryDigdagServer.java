package acceptance;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.digdag.client.DigdagClient;
import io.digdag.core.Version;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.ProcessingException;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import static acceptance.TestUtils.main;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThat;

public class TemporaryDigdagServer
        implements TestRule
{
    private static final Logger log = LoggerFactory.getLogger(TemporaryDigdagServer.class);

    private static final ThreadFactory DAEMON_THREAD_FACTORY = new ThreadFactoryBuilder().setDaemon(true).build();

    private final TemporaryFolder temporaryFolder = new TemporaryFolder();
    private final Version version;

    private final String host;
    private final int port;
    private final String endpoint;

    private final ExecutorService executor;

    private Path configDirectory;
    private Path config;

    public TemporaryDigdagServer(Builder builder)
    {
        this.version = Objects.requireNonNull(builder.version, "version");

        this.host = "localhost";
        this.port = 65432;
        this.endpoint = "http://" + host + ":" + port;

        this.executor = Executors.newSingleThreadExecutor(DAEMON_THREAD_FACTORY);
    }

    @Override
    public Statement apply(Statement base, Description description)
    {
        return temporaryFolder.apply(new Statement()
        {
            @Override
            public void evaluate()
                    throws Throwable
            {
                before();
                try {
                    base.evaluate();
                }
                finally {
                    after();
                }
            }
        }, description);
    }

    private void before()
            throws Throwable
    {
        try {
            this.configDirectory = temporaryFolder.newFolder().toPath();
            this.config = Files.createFile(configDirectory.resolve("config"));
        }
        catch (IOException e) {
            throw Throwables.propagate(e);
        }

        executor.execute(() -> main(version, "server", "-m", "-c", config.toString()));

        // Poll and wait for server to come up
        for (int i = 0; i < 30; i++) {
            DigdagClient client = DigdagClient.builder()
                    .host(host)
                    .port(port)
                    .build();
            try {
                client.getProjects();
                break;
            }
            catch (ProcessingException e) {
                assertThat(e.getCause(), instanceOf(ConnectException.class));
                log.debug("Waiting for server to come up...");
            }
            Thread.sleep(1000);
        }
    }

    private void after()
    {
        executor.shutdownNow();
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public String endpoint()
    {
        return endpoint;
    }

    public String host()
    {
        return host;
    }

    public int port()
    {
        return port;
    }

    public static TemporaryDigdagServer of()
    {
        return builder().build();
    }

    public static TemporaryDigdagServer of(Version version)
    {
        return builder().version(version).build();

    }

    public static class Builder
    {
        private Builder()
        {
        }

        private Version version = Version.buildVersion();

        public Builder version(Version version)
        {
            this.version = version;
            return this;
        }

        TemporaryDigdagServer build()
        {
            return new TemporaryDigdagServer(this);
        }
    }
}