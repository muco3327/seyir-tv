const https = require("https");
https.get("https://api.github.com/repos/muco3327/seyir-tv/actions/runs/34057620194/jobs", {
  headers: { "User-Agent": "SeyirTV-Checker" }
}, res => {
  let data = "";
  res.on("data", chunk => data += chunk);
  res.on("end", () => {
    const json = JSON.parse(data);
    const jobs = json.jobs || [];
    jobs.forEach(j => {
      console.log(`Job: ${j.name}, conclusion: ${j.conclusion}`);
      (j.steps || []).forEach(s => {
        console.log(`  Step: ${s.name}, conclusion: ${s.conclusion}`);
      });
    });
  });
});
