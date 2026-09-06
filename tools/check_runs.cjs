const https = require("https");
https.get("https://api.github.com/repos/muco3327/seyir-tv/actions/runs", {
  headers: { "User-Agent": "SeyirTV-Checker" }
}, res => {
  let data = "";
  res.on("data", chunk => data += chunk);
  res.on("end", () => {
    const json = JSON.parse(data);
    const runs = json.workflow_runs || [];
    runs.forEach(r => {
      console.log(`Run ID: ${r.id}, event: ${r.event}, conclusion: ${r.conclusion}, html: ${r.html_url}`);
    });
  });
});
