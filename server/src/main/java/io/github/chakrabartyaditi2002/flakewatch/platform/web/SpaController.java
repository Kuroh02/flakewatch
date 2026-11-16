package io.github.chakrabartyaditi2002.flakewatch.platform.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the React dashboard for its client-side routes (PLAN §13), so a shared link such as
 * {@code /p/demo/tests?status=FLAKY} works on a fresh page load.
 */
@Controller
public class SpaController {

    @GetMapping({"/", "/p/**"})
    public String index() {
        return "forward:/index.html";
    }
}
