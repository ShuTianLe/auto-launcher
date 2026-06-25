import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { ConsoleApp } from "@/components/ConsoleApp";
import { AUTH_COOKIE, isValidSessionToken } from "@/lib/auth";

export const dynamic = "force-dynamic";

export default async function HomePage() {
  const token = (await cookies()).get(AUTH_COOKIE)?.value;
  if (!isValidSessionToken(token)) {
    redirect("/login");
  }

  return <ConsoleApp />;
}
