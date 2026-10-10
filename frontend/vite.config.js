
import react from "@vitejs/plugin-react";
import path from "node:path";

export default {
  base: "/",
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(import.meta.dirname, "src"),
    },
  },
};
